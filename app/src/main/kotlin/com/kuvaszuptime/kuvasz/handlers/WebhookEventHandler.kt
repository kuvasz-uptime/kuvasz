package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
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
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
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
                logger.error("The event cannot be sent to the webhook target: ${ex.message}")
            }
        )

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            filterTargetConfigs(event).forEach { target ->
                when (event) {
                    is HttpMonitorUpEvent, is PushMonitorUpEvent -> {
                        if (previousEvent != null) {
                            assembleAndSendRequest(event, target as WebhookNotificationConfig).handleResponse()
                        }
                    }

                    is HttpMonitorDownEvent, is PushMonitorDownEvent ->
                        assembleAndSendRequest(event, target as WebhookNotificationConfig).handleResponse()
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
                            assembleAndSendRequest(event, target as WebhookNotificationConfig).handleResponse()
                        }
                    }

                    is SSLInvalidEvent, is SSLWillExpireEvent ->
                        assembleAndSendRequest(event, target as WebhookNotificationConfig).handleResponse()
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun assembleAndSendRequest(event: MonitorEvent<*>, config: WebhookNotificationConfig): Single<String> {
        val template = config.payloadTemplate

        return if (template.isNullOrBlank()) {
            webhookService.sendGenericWebhookEvent(config, messageFactory.fromMonitorEvent(event))
        } else {
            val payload = try {
                messageFactory.fromMonitorEvent(event, template)
            } catch (ex: Exception) {
                logger.error("Failed to parse webhook template: ${ex.message}")
                return Single.error(ex)
            }
            webhookService.sendTemplatedWebhookEvent(config, payload)
        }
    }
}
