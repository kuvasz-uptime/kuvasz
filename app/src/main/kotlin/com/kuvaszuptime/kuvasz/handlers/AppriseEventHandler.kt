package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.AppriseNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.AppriseService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = AppriseNotificationConfig::class)
class AppriseEventHandler(
    eventDispatcher: EventDispatcher,
    private val appriseService: AppriseService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<AppriseEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.APPRISE

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        filterMaintenanceTargets(event).forEach { target ->
            appriseService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            appriseService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            appriseService.sendEvent(target, event).handleResponse()
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        filterTargetConfigs(event).forEach { target ->
            appriseService.sendEvent(target, event).handleResponse()
        }
    }
}
