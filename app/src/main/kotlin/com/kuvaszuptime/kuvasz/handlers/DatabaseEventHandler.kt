package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.DnsUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.DockerUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.TcpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class DatabaseEventHandler(
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val tcpUptimeEventRepository: TcpUptimeEventRepository,
    private val dnsUptimeEventRepository: DnsUptimeEventRepository,
    private val dockerUptimeEventRepository: DockerUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val integrationRepository: IntegrationRepository,
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = loggerFor<DatabaseEventHandler>()
    }

    /**
     * Persists the uptime event. If [txCtx] is given, it's written as a part of that transaction (which saves the
     * caller a second connection), so the event must not be dispatched before that transaction is committed: the
     * handlers wouldn't see it from their own connections otherwise.
     */
    fun handleUptimeMonitorEvent(currentEvent: UptimeMonitorEvent, txCtx: DSLContext? = null) {
        val monitor = currentEvent.monitor
        val ctx = txCtx ?: dslContext

        currentEvent.previousEvent?.let { previousEvent ->
            logger.debug(
                "A previous event was found for [${monitor.name}] with ID: ${previousEvent.id} " +
                    "and status: ${previousEvent.status}. The current event's status is ${currentEvent.uptimeStatus}."
            )
            if (currentEvent.statusNotEquals(previousEvent)) {
                logger.debug(
                    "[${monitor.name}] The status of the previous event is different from the " +
                        "current event. Ending the previous event and inserting a new one."
                )
                // A savepoint, if it's a part of the caller's transaction
                ctx.transactionResult { config ->
                    val nestedCtx = config.dsl()
                    endUptimeEvent(previousEvent, currentEvent, nestedCtx)
                    insertUptimeEvent(currentEvent, nestedCtx)
                }
                logger.debug(
                    "[${monitor.name}] The previous event has been ended and a new one " +
                        "has been inserted."
                )
            } else {
                logger.debug(
                    "[${monitor.name}] The status of the previous event is the same as the current " +
                        "event. Updating the updatedAt timestamp of the previous event."
                )
                updateEvent(currentEvent, previousEvent, ctx)
            }
        } ?: run {
            logger.debug("A previous event was not found for [${monitor.name}], creating a new one")
            insertUptimeEvent(currentEvent, ctx)
        }
    }

    private fun endUptimeEvent(
        previousEvent: UptimeEventRecord,
        currentEvent: UptimeMonitorEvent,
        txCtx: DSLContext
    ) =
        when (currentEvent) {
            is HttpUptimeMonitorEvent -> httpUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
            is PushUptimeMonitorEvent -> pushUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
            is IcmpUptimeMonitorEvent -> icmpUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
            is TcpUptimeMonitorEvent -> tcpUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
            is DnsUptimeMonitorEvent -> dnsUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
            is DockerUptimeMonitorEvent -> dockerUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
        }

    private fun insertUptimeEvent(currentEvent: UptimeMonitorEvent, txCtx: DSLContext? = null): UptimeEventRecord {
        val integrations = currentEvent.notifiedIntegrations()
        return when (currentEvent) {
            is HttpUptimeMonitorEvent ->
                httpUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)

            is PushUptimeMonitorEvent ->
                pushUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)

            is IcmpUptimeMonitorEvent ->
                icmpUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)

            is TcpUptimeMonitorEvent ->
                tcpUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)

            is DnsUptimeMonitorEvent ->
                dnsUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)

            is DockerUptimeMonitorEvent ->
                dockerUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx, integrations)
        }
    }

    /**
     * The integrations an event is sent to when it opens a new uptime event. Mirrors [NotificationEventHandler], which
     * doesn't notify about the very first UP event of a monitor.
     */
    private fun UptimeMonitorEvent.notifiedIntegrations(): Array<IntegrationID> =
        if (isUp() && previousEvent == null) {
            emptyArray()
        } else {
            integrationRepository.resolveTargetIntegrationIds(this).toTypedArray()
        }

    private fun SSLMonitorEvent.notifiedIntegrations(): Array<IntegrationID> =
        if (this is SSLValidEvent && previousEvent == null) {
            emptyArray()
        } else {
            integrationRepository.resolveTargetIntegrationIds(this).toTypedArray()
        }

    private fun updateEvent(currentEvent: UptimeMonitorEvent, previousEvent: UptimeEventRecord, ctx: DSLContext) =
        when (currentEvent) {
            is HttpUptimeMonitorEvent -> httpUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
            is PushUptimeMonitorEvent -> pushUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
            is IcmpUptimeMonitorEvent -> icmpUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
            is TcpUptimeMonitorEvent -> tcpUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
            is DnsUptimeMonitorEvent -> dnsUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
            is DockerUptimeMonitorEvent -> dockerUptimeEventRepository.updateEvent(previousEvent.id, currentEvent, ctx)
        }

    fun handleSSLMonitorEvent(currentEvent: SSLMonitorEvent) {
        currentEvent.previousEvent?.let { previousEvent ->
            if (currentEvent.statusNotEquals(previousEvent)) {
                dslContext.transaction { config ->
                    sslEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt, config.dsl())
                    sslEventRepository.insertFromMonitorEvent(
                        event = currentEvent,
                        ctx = config.dsl(),
                        integrations = currentEvent.notifiedIntegrations(),
                    )
                }
            } else {
                sslEventRepository.updateEvent(previousEvent.id, currentEvent)
            }
        } ?: sslEventRepository.insertFromMonitorEvent(
            event = currentEvent,
            integrations = currentEvent.notifiedIntegrations(),
        )
    }
}
