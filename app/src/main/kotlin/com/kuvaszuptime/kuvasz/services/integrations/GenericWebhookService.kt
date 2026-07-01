package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.handlers.toIntegrationEventType
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.NotifiableEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.WebhookHttpMethod
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
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

    fun sendMessage(
        httpMethod: WebhookHttpMethod,
        url: URI,
        headers: Map<String, String>,
        payload: Any,
    ): Single<HttpResponse<ByteBuffer<*>>> {
        val request = when (httpMethod) {
            WebhookHttpMethod.POST -> HttpRequest.POST(url, payload)
            WebhookHttpMethod.PUT -> HttpRequest.PUT(url, payload)
            WebhookHttpMethod.PATCH -> HttpRequest.PATCH(url, payload)
            WebhookHttpMethod.GET -> HttpRequest.GET(url)
        }
        val effectiveContentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, DEFAULT_MEDIA_TYPE)
        if (httpMethod != WebhookHttpMethod.GET) {
            request.contentType(effectiveContentType)
        }
        headers.filter { it.key != HttpHeaders.CONTENT_TYPE }.forEach { request.header(it.key, it.value) }

        return Single.fromPublisher(client.exchange(request))
    }
}

@Singleton
@Requires(bean = GenericWebhookClient::class)
class GenericWebhookService(
    private val client: GenericWebhookClient,
    private val messageFactory: WebhookMessageFactory,
) : TestableNotificationService<WebhookNotificationConfig> {

    companion object {
        private val logger = loggerFor<GenericWebhookService>()
    }

    private val testHttpMonitorRecord = HttpMonitorRecord().apply {
        id = 1
        name = "Test monitor"
        sensitiveUrl = false
        url = "https://test.monitor"
    }
    private val testPushMonitorRecord = PushMonitorRecord().apply {
        id = 2
        name = "Test monitor"
    }

    @Suppress("MagicNumber")
    private val testIcmpMonitorRecord = IcmpMonitorRecord().apply {
        id = 3
        name = "Test monitor"
    }

    @Suppress("MagicNumber")
    val testEvents: List<MonitorEvent<out MonitorRecord>> = listOf(
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
        IcmpMonitorDownEvent(
            monitor = testIcmpMonitorRecord,
            error = "Test ICMP error",
            previousEvent = null,
            packetLossPercentage = 100,
        ),
        IcmpMonitorUpEvent(
            monitor = testIcmpMonitorRecord,
            previousEvent = null,
            latencyInMs = 42,
            packetLossPercentage = 0,
        ),
    )

    fun sendWebhookEvent(target: WebhookNotificationConfig, event: NotifiableEvent): Single<out Any> =
        client.sendMessage(
            url = prepareUrl(target, event),
            payload = preparePayload(target, event),
            headers = prepareHeaders(target, event),
            httpMethod = target.httpMethod,
        )

    private fun prepareHeaders(target: WebhookNotificationConfig, event: NotifiableEvent): Map<String, String> =
        target.requestHeaders.orEmpty().mapValues { (_, value) ->
            messageFactory.fromEvent(event, value)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun preparePayload(target: WebhookNotificationConfig, event: NotifiableEvent): Any {
        val template = target.payloadTemplate

        return if (template.isNullOrBlank()) {
            messageFactory.fromEvent(event)
        } else {
            try {
                messageFactory.fromEvent(event, template)
            } catch (ex: Exception) {
                logger.error("Failed to parse webhook template: ${ex.message}")
                throw ex
            }
        }
    }

    private fun prepareUrl(target: WebhookNotificationConfig, event: NotifiableEvent) =
        messageFactory.fromEvent(event, target.url).toUri()

    override fun sendTestMessage(integrationConfig: WebhookNotificationConfig): Single<NotificationTestResult> {
        val ignoredEventTypes = integrationConfig.excludedEvents.orEmpty()
        val results = testEvents.mapNotNull { testEvent ->
            if (!ignoredEventTypes.contains(testEvent.toIntegrationEventType())) {
                sendWebhookEvent(integrationConfig, testEvent)
            } else null
        }

        return Observable
            .fromIterable(results)
            .concatMapSingle { it }
            .reduce { accumulator, next -> "$accumulator, $next" }
            .toSingle().toNotificationTestResult()
    }
}
