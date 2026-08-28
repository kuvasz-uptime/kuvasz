package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import com.kuvaszuptime.kuvasz.models.events.formatters.toSeverity
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PushoverNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.PushoverService
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = PushoverNotificationConfig::class)
class PushoverEventHandler(
    eventDispatcher: EventDispatcher,
    private val pushoverService: PushoverService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<PushoverEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.PUSHOVER

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        filterMaintenanceTargets(event).forEach { target ->
            pushoverService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            pushoverService.sendEvent(target, event).handleResponse()
            if (event.toSeverity() == MessageSeverity.OK) {
                // A recovery notification on its own doesn't stop an emergency one, only a cancellation does
                pushoverService.cancelEmergency(target, event)?.handleResponse()
            }
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            pushoverService.sendEvent(target, event).handleResponse()
            if (event.toSeverity() == MessageSeverity.OK) {
                // A recovery notification on its own doesn't stop an emergency one, only a cancellation does
                pushoverService.cancelEmergency(target, event)?.handleResponse()
            }
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        filterTargetConfigs(event).forEach { target ->
            pushoverService.sendEvent(target, event).handleResponse()
        }
    }
}
