package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.jooq.enums.IntegrationThreadSubject
import com.kuvaszuptime.kuvasz.jooq.tables.records.IntegrationThreadRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import com.kuvaszuptime.kuvasz.models.events.formatters.SlackTextFormatter
import com.kuvaszuptime.kuvasz.models.events.formatters.toSeverity
import com.kuvaszuptime.kuvasz.models.handlers.IncidentNoteMirroring
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.uptimeIncidentType
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.IntegrationThreadRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotService
import com.kuvaszuptime.kuvasz.services.integrations.SlackColor
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

/**
 * Posts the notifications into threads: an incident (or a maintenance) opens a thread with a root message, the later
 * messages about the same subject are replies in it, and the color of the root always shows the current state.
 * Everything that can't be correlated to an open thread is posted to the channel.
 */
@Context
@Requires(bean = SlackBotNotificationConfig::class)
class SlackBotEventHandler(
    eventDispatcher: EventDispatcher,
    private val slackBotService: SlackBotService,
    private val integrationThreadRepository: IntegrationThreadRepository,
    private val incidentRepository: IncidentRepository,
    private val integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<SlackBotEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.SLACK_BOT

    private val formatter = SlackTextFormatter

    init {
        eventDispatcher.subscribeToIncidentNoteEvents { event -> handleIncidentNoteEvent(event) }
    }

    /**
     * Mirrors the notes of an incident into its threads (as far as the integration is configured to do so), even after
     * its resolution. The notes of an incident without a thread are not posted anywhere, because they wouldn't make
     * sense without the incident they belong to.
     */
    private fun handleIncidentNoteEvent(event: IncidentNoteEvent) {
        val text = formatter.toFormattedMessage(event)
        integrationThreadRepository.findBySubject(event.note.incidentType.threadSubject, event.note.eventId)
            .forEach { thread ->
                // Only to the integrations that are still configured and enabled
                (integrationRepository.enabledIntegrations[thread.integrationId] as? SlackBotNotificationConfig)
                    ?.takeIf { target -> target.mirrors(event.note) }
                    ?.let { target ->
                        slackBotService.postMessage(target, text, color = null, threadTs = thread.ts).handleResponse()
                    }
            }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        val incidentType = event.monitor.monitorId().type.uptimeIncidentType
        val text = formatter.toFormattedMessage(event)
        val color = event.toSeverity().color

        filterTargetConfigs(event).slackBotConfigs().forEach { target ->
            if (event.isUp()) {
                resolveIncidentThread(target, incidentType.threadSubject, event.previousEvent?.id, text, color)
            } else {
                openIncidentThread(target, incidentType, event, text, color)
            }
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        val text = formatter.toFormattedMessage(event)
        val color = event.toSeverity().color

        filterTargetConfigs(event).slackBotConfigs().forEach { target ->
            when (event) {
                is SSLValidEvent ->
                    resolveIncidentThread(target, IncidentType.SSL.threadSubject, event.previousEvent?.id, text, color)

                // An expiring certificate that becomes invalid (or vice versa) is still the same story
                is SSLInvalidEvent, is SSLWillExpireEvent -> {
                    val thread = event.previousEvent?.id?.let { previousEventId ->
                        integrationThreadRepository.find(target.id, IntegrationThreadSubject.SSL, previousEventId)
                    }
                    if (thread != null) {
                        continueIncidentThread(target, thread, event, text, color)
                    } else {
                        openIncidentThread(target, IncidentType.SSL, event, text, color)
                    }
                }
            }
        }
    }

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        val text = formatter.toFormattedMessage(event)
        val color = event.color

        filterMaintenanceTargets(event).slackBotConfigs().forEach { target ->
            when (event) {
                is MaintenanceWindowStartEvent -> openThread(
                    target = target,
                    subjectType = IntegrationThreadSubject.MAINTENANCE,
                    subjectId = { event.window.id },
                    text = text,
                    color = color,
                    reaction = target.triggerEmoji,
                )

                is MaintenanceWindowEndEvent -> {
                    val subject = IntegrationThreadSubject.MAINTENANCE
                    val thread = integrationThreadRepository.find(target.id, subject, event.window.id)
                    if (thread != null) {
                        // A maintenance thread is over with its end, unlike an incident thread, which can get notes
                        integrationThreadRepository.delete(target.id, subject, event.window.id)
                        resolveThread(target, thread, text, color)
                    } else {
                        post(target, text, color)
                    }
                }
            }
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        val text = formatter.toFormattedMessage(event)
        filterTargetConfigs(event).slackBotConfigs().forEach { target ->
            post(target, text, MessageSeverity.INFO.color)
        }
    }

    override fun handleDockerContainerRestartedEvent(event: DockerContainerRestartedEvent) {
        val text = formatter.toFormattedMessage(event)
        filterTargetConfigs(event).slackBotConfigs().forEach { target ->
            post(target, text, MessageSeverity.WARNING.color)
        }
    }

    private fun Set<IntegrationConfig>.slackBotConfigs() = filterIsInstance<SlackBotNotificationConfig>()

    private fun post(target: SlackBotNotificationConfig, text: String, color: SlackColor) {
        slackBotService.postMessage(target, text, color).handleResponse()
    }

    /**
     * The thread of an incident is keyed by the event that opened it, which has been persisted right before the
     * event was dispatched, with the dispatch time as its start.
     */
    private fun openIncidentThread(
        target: SlackBotNotificationConfig,
        incidentType: IncidentType,
        event: MonitorEvent<*>,
        text: String,
        color: SlackColor,
    ) = openThread(
        target = target,
        subjectType = incidentType.threadSubject,
        subjectId = { incidentRepository.findEventIdStartedAt(incidentType, event.monitor.id, event.dispatchedAt) },
        text = text,
        color = color,
        reaction = target.triggerEmoji,
    )

    private fun openThread(
        target: SlackBotNotificationConfig,
        subjectType: IntegrationThreadSubject,
        subjectId: () -> Long?,
        text: String,
        color: SlackColor,
        reaction: String?,
    ) {
        slackBotService.postMessage(target, text, color)
            .doOnSuccess { ts ->
                val id = subjectId()
                if (id == null) {
                    logger.warn("The subject of the $subjectType thread $ts cannot be found, it won't be continued")
                } else {
                    integrationThreadRepository.upsert(
                        IntegrationThreadRecord()
                            .setIntegrationId(target.id)
                            .setSubjectType(subjectType)
                            .setSubjectId(id)
                            .setTs(ts)
                            .setRootText(text)
                    )
                }
                reaction?.let { emoji -> slackBotService.addReaction(target, ts, emoji).handleResponse() }
            }
            .handleResponse()
    }

    private fun continueIncidentThread(
        target: SlackBotNotificationConfig,
        thread: IntegrationThreadRecord,
        event: SSLMonitorEvent,
        text: String,
        color: SlackColor,
    ) {
        // The new event belongs to the same thread too, so that the notes of its incident end up there as well
        val eventId = incidentRepository.findEventIdStartedAt(IncidentType.SSL, event.monitor.id, event.dispatchedAt)
        if (eventId == null) {
            logger.warn("The event that continues the SSL thread ${thread.ts} cannot be found, it won't be continued")
        } else {
            integrationThreadRepository.upsert(
                IntegrationThreadRecord()
                    .setIntegrationId(thread.integrationId)
                    .setSubjectType(thread.subjectType)
                    .setSubjectId(eventId)
                    .setTs(thread.ts)
                    .setRootText(thread.rootText)
            )
        }

        replyThenUpdateRoot(target, thread, text, color, rootText = thread.rootText)
    }

    private fun resolveIncidentThread(
        target: SlackBotNotificationConfig,
        subjectType: IntegrationThreadSubject,
        previousEventId: Long?,
        text: String,
        color: SlackColor,
    ) {
        val thread = previousEventId?.let { id ->
            integrationThreadRepository.find(integrationId = target.id, subjectType = subjectType, subjectId = id)
        }
        if (thread != null) {
            // The thread itself is kept, because notes can be added to the incident even after its resolution
            resolveThread(target, thread, text, color)
        } else {
            post(target, text, color)
        }
    }

    private fun resolveThread(
        target: SlackBotNotificationConfig,
        thread: IntegrationThreadRecord,
        text: String,
        color: SlackColor,
    ) {
        // The root tells that its story is over not only with its color, but by striking through its headline too
        replyThenUpdateRoot(target, thread, text, color, rootText = formatter.strikethroughHeadline(thread.rootText)) {
            target.triggerEmoji?.let { emoji ->
                slackBotService.removeReaction(integrationConfig = target, ts = thread.ts, emoji = emoji)
                    .handleResponse()
            }
            target.resolutionEmoji?.let { emoji ->
                slackBotService.addReaction(integrationConfig = target, ts = thread.ts, emoji = emoji).handleResponse()
            }
        }
    }

    /**
     * Replies in the thread, and updates its root only once the reply is done (successfully or not), then runs
     * [afterwards]. Slack updates the root itself on every reply (e.g. its reply count), which can overwrite an update
     * of the root sent at the same time. If the reply has been posted to the channel instead, because the root is gone,
     * there's nothing to update.
     */
    private fun replyThenUpdateRoot(
        target: SlackBotNotificationConfig,
        thread: IntegrationThreadRecord,
        text: String,
        color: SlackColor,
        rootText: String,
        afterwards: () -> Unit = {},
    ) {
        slackBotService.reply(integrationConfig = target, threadTs = thread.ts, text = text, color = color)
            .doOnEvent { reply, _ ->
                if (reply?.inThread != false) {
                    slackBotService.recolorRoot(target, ts = thread.ts, text = rootText, color = color)
                        .doFinally(afterwards)
                        .handleResponse()
                }
            }
            .handleResponse()
    }
}

private fun SlackBotNotificationConfig.mirrors(note: IncidentNoteDto): Boolean = when (incidentNotes) {
    IncidentNoteMirroring.ALL -> true
    IncidentNoteMirroring.PUBLIC -> note.public
    IncidentNoteMirroring.NONE -> false
}

private val SlackBotNotificationConfig.triggerEmoji: String?
    get() = triggerReaction?.takeIf { emoji -> emoji.isNotBlank() }

private val SlackBotNotificationConfig.resolutionEmoji: String?
    get() = resolutionReaction?.takeIf { emoji -> emoji.isNotBlank() }

private val MessageSeverity.color: SlackColor
    get() = when (this) {
        MessageSeverity.CRITICAL -> SlackColor.RED
        MessageSeverity.WARNING -> SlackColor.YELLOW
        MessageSeverity.OK -> SlackColor.GREEN
        MessageSeverity.INFO -> SlackColor.BLUE
    }

// A maintenance is a planned, temporary degradation, so its start is a warning, unlike its INFO severity elsewhere
private val MaintenanceWindowEvent.color: SlackColor
    get() = when (this) {
        is MaintenanceWindowStartEvent -> SlackColor.YELLOW
        is MaintenanceWindowEndEvent -> SlackColor.GREEN
    }

private val IncidentType.threadSubject: IntegrationThreadSubject
    get() = when (this) {
        IncidentType.HTTP -> IntegrationThreadSubject.HTTP
        IncidentType.SSL -> IntegrationThreadSubject.SSL
        IncidentType.PUSH -> IntegrationThreadSubject.PUSH
        IncidentType.ICMP -> IntegrationThreadSubject.ICMP
        IncidentType.TCP -> IntegrationThreadSubject.TCP
        IncidentType.DNS -> IntegrationThreadSubject.DNS
        IncidentType.DOCKER -> IntegrationThreadSubject.DOCKER
    }
