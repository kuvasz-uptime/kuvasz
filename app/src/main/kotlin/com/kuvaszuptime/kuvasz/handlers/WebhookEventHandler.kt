package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.GenericWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import org.slf4j.LoggerFactory

// TODO test
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
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { it as WebhookNotificationConfig }
            when (event) {
                is HttpMonitorUpEvent, is PushMonitorUpEvent -> {
                    if (previousEvent != null) {
                        event.handleSending(integrations)
                    }
                }

                is HttpMonitorDownEvent, is PushMonitorDownEvent -> event.handleSending(integrations)
            }
        }
    }

    private fun MonitorEvent<*>.handleSending(configs: List<WebhookNotificationConfig>) {
        configs.filterByEventType(this).forEach { integrationConfig ->
            assembleAndSendRequest(this, integrationConfig).handleResponse()
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { it as WebhookNotificationConfig }
            when (event) {
                is SSLValidEvent -> {
                    if (previousEvent != null) {
                        event.handleSending(integrations)
                    }
                }

                is SSLInvalidEvent, is SSLWillExpireEvent -> event.handleSending(integrations)
            }
        }
    }

    private fun WebhookNotificationConfig.supportsEventType(eventType: WebhookEventType): Boolean =
        eventTypes.isNullOrEmpty() || eventTypes.orEmpty().contains(eventType)

    @Suppress("NotImplementedDeclaration")
    private fun List<WebhookNotificationConfig>.filterByEventType(
        event: MonitorEvent<*>,
    ): List<WebhookNotificationConfig> {
        return filter { config ->
            when (event) {
                is SSLInvalidEvent -> config.supportsEventType(WebhookEventType.SSL_INVALID)
                is SSLValidEvent -> config.supportsEventType(WebhookEventType.SSL_VALID)
                is SSLWillExpireEvent -> config.supportsEventType(WebhookEventType.SSL_WILL_EXPIRE)
                is HttpMonitorDownEvent -> config.supportsEventType(WebhookEventType.HTTP_DOWN)
                is HttpMonitorUpEvent -> config.supportsEventType(WebhookEventType.HTTP_UP)
                is PushMonitorDownEvent -> config.supportsEventType(WebhookEventType.PUSH_DOWN)
                is PushMonitorUpEvent -> config.supportsEventType(WebhookEventType.PUSH_UP)
                is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
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
