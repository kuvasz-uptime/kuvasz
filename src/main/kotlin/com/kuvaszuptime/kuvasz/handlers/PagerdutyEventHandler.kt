package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerPayload
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.PagerdutyAPIClient
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import org.slf4j.LoggerFactory

@Context
@Requires(property = "handler-config.pagerduty-event-handler.enabled", value = "true")
class PagerdutyEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val apiClient: PagerdutyAPIClient
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PagerdutyEventHandler::class.java)
    }

    init {
        subscribeToEvents()
    }

    internal fun subscribeToEvents() {
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
                    val responseBody = ex.response.getBody(String::class.java)
                    logger.error("The event cannot be sent to Pagerduty: $responseBody")
                }
            }
        )

    private val UptimeMonitorEvent.deduplicationKey: String
        get() = "kuvasz_uptime_${monitor.id}"

    private val SSLMonitorEvent.deduplicationKey: String
        get() = "kuvasz_ssl_${monitor.id}"

    private fun UptimeMonitorEvent.handle() {
        if (monitor.pagerdutyIntegrationKey != null) {
            runWhenStateChanges { event ->
                when (event) {
                    is MonitorUpEvent -> {
                        if (previousEvent != null) {
                            val request = event.toResolveRequest(deduplicationKey)
                            apiClient.resolveAlert(request).handleResponse()
                        }
                    }
                    is MonitorDownEvent -> {
                        val request = event.toTriggerRequest(deduplicationKey)
                        apiClient.triggerAlert(request).handleResponse()
                    }
                }
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        if (monitor.pagerdutyIntegrationKey != null) {
            runWhenStateChanges { event ->
                when (event) {
                    is SSLValidEvent -> {
                        if (previousEvent != null) {
                            val request = event.toResolveRequest(deduplicationKey)
                            apiClient.resolveAlert(request).handleResponse()
                        }
                    }
                    is SSLInvalidEvent -> {
                        val request = event.toTriggerRequest(deduplicationKey)
                        apiClient.triggerAlert(request).handleResponse()
                    }
                    is SSLWillExpireEvent -> {
                        val request = event.toTriggerRequest(deduplicationKey, PagerdutySeverity.WARNING)
                        apiClient.triggerAlert(request).handleResponse()
                    }
                }
            }
        }
    }

    private fun MonitorEvent.toTriggerRequest(
        deduplicationKey: String,
        severity: PagerdutySeverity = PagerdutySeverity.CRITICAL
    ) =
        PagerdutyTriggerRequest(
            routingKey = monitor.pagerdutyIntegrationKey,
            dedupKey = deduplicationKey,
            payload = PagerdutyTriggerPayload(
                summary = toStructuredMessage().summary,
                source = monitor.url,
                severity = severity
            )
        )

    private fun MonitorEvent.toResolveRequest(deduplicationKey: String) =
        PagerdutyResolveRequest(
            routingKey = monitor.pagerdutyIntegrationKey,
            dedupKey = deduplicationKey
        )
}
