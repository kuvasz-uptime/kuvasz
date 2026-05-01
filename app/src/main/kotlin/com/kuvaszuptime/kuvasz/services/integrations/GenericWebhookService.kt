package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.factories.getWebhookEventType
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
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
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import java.net.URI

@Singleton
@Requires(property = WebhookNotificationConfig.CONFIG_PREFIX)
class GenericWebhookClient(@param:Client private val client: HttpClient) {

    companion object {
        private const val DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON
    }

    @Retryable
    fun sendMessage(webhookUrl: URI, message: GenericWebhookMessage, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        return sendRequestWithHeaders(req, headers)
    }

    @Retryable
    fun sendMessage(webhookUrl: URI, payload: String, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, payload)
        return sendRequestWithHeaders(req, headers)
    }

    private fun sendRequestWithHeaders(request: MutableHttpRequest<*>, headers: Map<String, String>): Single<String> {
        val effectiveContentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, DEFAULT_MEDIA_TYPE)
        // TODO test correct content-type header in different scenarios
        request.contentType(effectiveContentType)
        headers.filter { it.key != HttpHeaders.CONTENT_TYPE }.forEach { request.header(it.key, it.value) }

        // TODO test requests with mockServer
        return Single.fromPublisher(client.retrieve(request, String::class.java))
    }
}

@Singleton
@Requires(bean = GenericWebhookClient::class)
class GenericWebhookService(
    private val client: GenericWebhookClient,
    private val messageFactory: WebhookMessageFactory,
) : TestableNotificationService<WebhookNotificationConfig> {

    private val testHttpMonitorRecord = HttpMonitorRecord().apply {
        name = "Test HTTP monitor"
        sensitiveUrl = false
        url = "https://irrelevant"
    }
    private val testPushMonitorRecord = PushMonitorRecord().apply {
        name = "Test push monitor"
    }

    @Suppress("MagicNumber")
    private val testEvents: List<MonitorEvent<*>> = listOf(
        HttpMonitorDownEvent(
            monitor = testHttpMonitorRecord,
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            error = Exception("Test error"),
            previousEvent = null,
        ),
        HttpMonitorUpEvent(
            monitor = testHttpMonitorRecord,
            status = HttpStatus.OK,
            latency = 123,
            previousEvent = null,
        ),
        SSLWillExpireEvent(
            monitor = testHttpMonitorRecord,
            certInfo = CertificateInfo(validTo = getCurrentTimestamp().plusHours(3)),
            previousEvent = null,
        ),
        SSLInvalidEvent(
            monitor = testHttpMonitorRecord,
            error = SSLValidationError("Test SSL invalid error"),
            previousEvent = null,
        ),
        SSLValidEvent(
            monitor = testHttpMonitorRecord,
            certInfo = CertificateInfo(validTo = getCurrentTimestamp().plusDays(30)),
            previousEvent = null,
        ),
        PushMonitorDownEvent(
            monitor = testPushMonitorRecord,
            error = "Test push monitor error",
            previousEvent = null,
        ),
        PushMonitorUpEvent(
            monitor = testPushMonitorRecord,
            previousEvent = null,
        ),
    )

    fun sendGenericWebhookEvent(integrationConfig: IntegrationConfig, message: GenericWebhookMessage): Single<String> {
        val config = integrationConfig as WebhookNotificationConfig
        val webhookUrl = config.url.toUri()

        return client.sendMessage(webhookUrl, message, config.requestHeaders.orEmpty())
    }

    fun sendTemplatedWebhookEvent(integrationConfig: IntegrationConfig, payload: String): Single<String> {
        val config = integrationConfig as WebhookNotificationConfig
        val webhookUrl = config.url.toUri()

        return client.sendMessage(webhookUrl, payload, config.requestHeaders.orEmpty())
    }

    // TODO test
    override fun sendTestMessage(integrationConfig: WebhookNotificationConfig): Single<NotificationTestResult> {
        val handledEventTypes = integrationConfig.eventTypes.orEmpty().ifEmpty { WebhookEventType.entries }
        val results = testEvents.mapNotNull { testEvent ->
            @Suppress("NotImplementedDeclaration")
            val webhookEventType = when (testEvent) {
                is UptimeMonitorEvent -> testEvent.getWebhookEventType()
                is SSLMonitorEvent -> testEvent.getWebhookEventType()
                else -> throw NotImplementedError()
            }
            if (handledEventTypes.contains(webhookEventType)) {
                val template = integrationConfig.payloadTemplate
                if (template.isNullOrEmpty()) {
                    sendGenericWebhookEvent(integrationConfig, messageFactory.fromMonitorEvent(testEvent))
                } else {
                    sendTemplatedWebhookEvent(integrationConfig, messageFactory.fromMonitorEvent(testEvent, template))
                }
            } else null
        }
        val combinedResult = Observable.fromIterable(results)

        return combinedResult.concatMapSingle { it }
            .reduce { accumulator, next -> "$accumulator, $next" }
            .toSingle().toNotificationTestResult()
    }
}
