package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.MsTeamsWebhookService
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = MsTeamsNotificationConfig::class)
class MsTeamsEventHandler(
    eventDispatcher: EventDispatcher,
    private val msTeamsWebhookService: MsTeamsWebhookService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<MsTeamsEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.MS_TEAMS

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        filterMaintenanceTargets(event).forEach { target ->
            msTeamsWebhookService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            msTeamsWebhookService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            msTeamsWebhookService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        filterTargetConfigs(event).forEach { target ->
            msTeamsWebhookService.sendEvent(target, event).handleResponse()
        }
    }
}
