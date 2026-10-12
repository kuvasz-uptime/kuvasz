package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import com.kuvaszuptime.kuvasz.models.events.formatters.SlackTextFormatter
import com.kuvaszuptime.kuvasz.models.events.formatters.toSeverity
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.SlackBotNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SlackBotService
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = SlackBotNotificationConfig::class)
class SlackBotEventHandler(
    eventDispatcher: EventDispatcher,
    private val slackBotService: SlackBotService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<SlackBotEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.SLACK_BOT

    private val formatter = SlackTextFormatter

    override fun handleUptimeEvent(event: UptimeMonitorEvent) =
        postToAll(filterTargetConfigs(event), formatter.toFormattedMessage(event), event.toSeverity().color)

    override fun handleSSLEvent(event: SSLMonitorEvent) =
        postToAll(filterTargetConfigs(event), formatter.toFormattedMessage(event), event.toSeverity().color)

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) =
        postToAll(filterMaintenanceTargets(event), formatter.toFormattedMessage(event), event.color)

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) =
        postToAll(filterTargetConfigs(event), formatter.toFormattedMessage(event), MessageSeverity.INFO.color)

    override fun handleDockerContainerRestartedEvent(event: DockerContainerRestartedEvent) =
        postToAll(filterTargetConfigs(event), formatter.toFormattedMessage(event), MessageSeverity.WARNING.color)

    private fun postToAll(targets: Set<IntegrationConfig>, text: String, color: SlackColor) {
        targets.filterIsInstance<SlackBotNotificationConfig>().forEach { target ->
            slackBotService.postMessage(target, text, color.hex).handleResponse()
        }
    }
}

private enum class SlackColor(val hex: String) {
    RED("#d63939"),
    YELLOW("#f59f00"),
    GREEN("#2fb344"),
    BLUE("#066fd1"),
}

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
