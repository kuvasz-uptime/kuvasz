package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.GenericWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import org.slf4j.LoggerFactory

@Context
@Requires(bean = WebhookNotificationConfig::class)
class WebhookEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val webhookService: GenericWebhookService,
    integrationRepository: IntegrationRepository,
    private val messageFactory: WebhookMessageFactory,
) : AbstractIntegrationProvider(integrationRepository) {
    companion object {
        private val logger = LoggerFactory.getLogger(WebhookEventHandler::class.java)
    }

    init {
        subscribeToEvents()
        logger.info("Generic webhook event handler has been initialized")
    }

    override val integrationType: IntegrationType = IntegrationType.WEBHOOK

    private fun subscribeToEvents() {
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            logger.debug("An HttpMonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToHttpMonitorDownEvents { event ->
            logger.debug("An HttpMonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToPushMonitorEvents { event ->
            logger.debug("A PushMonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.debug("An SSLValidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.debug("An SSLInvalidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.debug("An SSLWillExpireEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
    }

    private fun Single<String>.handleResponse(): Disposable =
        subscribe(
            {
                logger.debug("The event has been successfully sent to the webhook target")
            },
            { ex ->
                if (ex is HttpClientResponseException) {
                    val responseBody = ex.response.getBodyAs<String>()
                    logger.error("The event cannot be sent to the webhook target: $responseBody")
                }
            }
        )

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { it as WebhookNotificationConfig }
            when (event) {
                is HttpMonitorUpEvent, is PushMonitorUpEvent -> {
                    if (previousEvent != null) {
                        integrations.forEach { integrationConfig ->
                            webhookService
                                .sendWebhookEvent(integrationConfig, messageFactory.fromUptimeEvent(event))
                                .handleResponse()
                        }
                    }
                }

                is HttpMonitorDownEvent, is PushMonitorDownEvent -> {
                    integrations.forEach { integrationConfig ->
                        webhookService
                            .sendWebhookEvent(integrationConfig, messageFactory.fromUptimeEvent(event))
                            .handleResponse()
                    }
                }
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { it as WebhookNotificationConfig }
            when (event) {
                is SSLValidEvent -> {
                    if (previousEvent != null) {
                        integrations.forEach { integrationConfig ->
                            webhookService
                                .sendWebhookEvent(integrationConfig, messageFactory.fromSslEvent(event))
                                .handleResponse()
                        }
                    }
                }

                is SSLInvalidEvent -> {
                    integrations.forEach { integrationConfig ->
                        webhookService
                            .sendWebhookEvent(integrationConfig, messageFactory.fromSslEvent(event))
                            .handleResponse()
                    }
                }

                is SSLWillExpireEvent -> {
                    integrations.forEach { integrationConfig ->
                        webhookService
                            .sendWebhookEvent(integrationConfig, messageFactory.fromSslEvent(event))
                            .handleResponse()
                    }
                }
            }
        }
    }
}
