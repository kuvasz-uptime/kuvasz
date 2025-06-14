package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerPayload
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.PagerdutyAPIClient
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import org.slf4j.LoggerFactory

@Context
@Requires(bean = PagerdutyConfig::class)
class PagerdutyEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val apiClient: PagerdutyAPIClient,
    integrationRepository: IntegrationRepository,
) : AbstractIntegrationProvider(integrationRepository) {
    companion object {
        private val logger = LoggerFactory.getLogger(PagerdutyEventHandler::class.java)
    }

    init {
        subscribeToEvents()
        logger.info("PagerDuty event handler has been initialized")
    }

    override val integrationType: IntegrationType = IntegrationType.PAGERDUTY

    private fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
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
                logger.debug("The event has been successfully sent to Pagerduty")
            },
            { ex ->
                if (ex is HttpClientResponseException) {
                    val responseBody = ex.response.getBodyAs<String>()
                    logger.error("The event cannot be sent to Pagerduty: $responseBody")
                }
            }
        )

    private val UptimeMonitorEvent.deduplicationKey: String
        get() = "kuvasz_uptime_${monitor.id}"

    private val SSLMonitorEvent.deduplicationKey: String
        get() = "kuvasz_ssl_${monitor.id}"

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { (it as PagerdutyConfig).integrationKey }
            when (event) {
                is MonitorUpEvent -> {
                    if (previousEvent != null) {
                        integrations.forEach { integrationKey ->
                            val request = createResolveRequest(
                                serviceKey = integrationKey,
                                deduplicationKey = deduplicationKey
                            )
                            apiClient.resolveAlert(request).handleResponse()
                        }
                    }
                }

                is MonitorDownEvent -> {
                    integrations.forEach { integrationKey ->
                        val request = event.toTriggerRequest(
                            serviceKey = integrationKey,
                            deduplicationKey = deduplicationKey
                        )
                        apiClient.triggerAlert(request).handleResponse()
                    }
                }
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            val integrations = filterTargetConfigs(event.monitor.integrations)
                .map { (it as PagerdutyConfig).integrationKey }
            when (event) {
                is SSLValidEvent -> {
                    if (previousEvent != null) {
                        integrations.forEach { integrationKey ->
                            val request = createResolveRequest(
                                serviceKey = integrationKey,
                                deduplicationKey = deduplicationKey
                            )
                            apiClient.resolveAlert(request).handleResponse()
                        }
                    }
                }

                is SSLInvalidEvent -> {
                    integrations.forEach { integrationKey ->
                        val request = event.toTriggerRequest(
                            serviceKey = integrationKey,
                            deduplicationKey = deduplicationKey
                        )
                        apiClient.triggerAlert(request).handleResponse()
                    }
                }

                is SSLWillExpireEvent -> {
                    integrations.forEach { integrationKey ->
                        val request = event.toTriggerRequest(
                            serviceKey = integrationKey,
                            deduplicationKey = deduplicationKey,
                            severity = PagerdutySeverity.WARNING
                        )
                        apiClient.triggerAlert(request).handleResponse()
                    }
                }
            }
        }
    }

    private fun MonitorEvent.toTriggerRequest(
        serviceKey: String,
        deduplicationKey: String,
        severity: PagerdutySeverity = PagerdutySeverity.CRITICAL
    ) =
        PagerdutyTriggerRequest(
            routingKey = serviceKey,
            dedupKey = deduplicationKey,
            payload = PagerdutyTriggerPayload(
                summary = toStructuredMessage().summary,
                source = monitor.url,
                severity = severity
            )
        )

    private fun createResolveRequest(serviceKey: String, deduplicationKey: String) =
        PagerdutyResolveRequest(
            routingKey = serviceKey,
            dedupKey = deduplicationKey
        )
}
