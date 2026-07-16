package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.GenericWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(bean = WebhookNotificationConfig::class)
class WebhookEventHandler(
    eventDispatcher: EventDispatcher,
    private val webhookService: GenericWebhookService,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<WebhookEventHandler>()

    override val integrationType: IntegrationType = IntegrationType.WEBHOOK

    init {
        logger.info("Generic webhook event handler has been initialized")
    }

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        filterMaintenanceTargets(event).forEach { target ->
            webhookService.sendWebhookEvent(target as WebhookNotificationConfig, event).handleResponse()
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            webhookService.sendWebhookEvent(target as WebhookNotificationConfig, event).handleResponse()
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            webhookService.sendWebhookEvent(target as WebhookNotificationConfig, event).handleResponse()
        }
    }
}
