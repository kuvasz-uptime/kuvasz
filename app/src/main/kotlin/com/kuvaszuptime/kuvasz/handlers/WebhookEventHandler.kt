package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
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
import io.reactivex.rxjava3.schedulers.Schedulers
import org.slf4j.LoggerFactory

@Context
@Requires(bean = WebhookNotificationConfig::class)
class WebhookEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val webhookService: GenericWebhookService,
    integrationRepository: IntegrationRepository,
) : AbstractIntegrationProvider(integrationRepository) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
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
            logger.debug(
                "A PushMonitorEvent (${event.toIntegrationEventType()}) has been received for " +
                    "monitor with ID: ${event.monitor.id}"
            )
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
        eventDispatcher.subscribeToIcmpMonitorUpEvents { event ->
            logger.debug("An IcmpMonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToIcmpMonitorDownEvents { event ->
            logger.debug("An IcmpMonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
    }

    private fun Single<String>.handleResponse(): Disposable =
        subscribeOn(Schedulers.io()).subscribe(
            {
                logger.debug("The message to your configured webhook has been successfully sent")
            },
            { ex ->
                val message = if (ex is HttpClientResponseException) {
                    ex.response.getBodyAs<String>() ?: "Empty response"
                } else {
                    ex.message
                }
                logger.error("The message cannot be sent to your configured webhook: $message")
            }
        )

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            filterTargetConfigs(event).forEach { target ->
                val webhookConfig = target as WebhookNotificationConfig
                when (event) {
                    is HttpMonitorUpEvent, is PushMonitorUpEvent, is IcmpMonitorUpEvent -> {
                        if (previousEvent != null) {
                            webhookService.sendWebhookEvent(webhookConfig, event).handleResponse()
                        }
                    }

                    is HttpMonitorDownEvent, is PushMonitorDownEvent, is IcmpMonitorDownEvent ->
                        webhookService.sendWebhookEvent(webhookConfig, event).handleResponse()
                }
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            filterTargetConfigs(event).forEach { target ->
                when (event) {
                    is SSLValidEvent -> {
                        if (previousEvent != null) {
                            webhookService.sendWebhookEvent(target as WebhookNotificationConfig, event).handleResponse()
                        }
                    }

                    is SSLInvalidEvent, is SSLWillExpireEvent ->
                        webhookService.sendWebhookEvent(target as WebhookNotificationConfig, event).handleResponse()
                }
            }
        }
    }
}
