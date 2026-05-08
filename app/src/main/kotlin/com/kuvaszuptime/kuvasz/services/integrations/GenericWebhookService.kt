package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.handlers.toIntegrationEventType
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
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
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.URI

@Singleton
@Requires(property = WebhookNotificationConfig.CONFIG_PREFIX)
class GenericWebhookClient(@param:Client private val client: HttpClient) {

    companion object {
        private const val DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_JSON
    }

    fun sendGenericMessage(
        webhookUrl: URI,
        message: GenericWebhookMessage,
        headers: Map<String, String>
    ): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        return sendRequestWithHeaders(req, headers)
    }

    fun sendTemplatedMessage(webhookUrl: URI, payload: String, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, payload)
        return sendRequestWithHeaders(req, headers)
    }

    private fun sendRequestWithHeaders(request: MutableHttpRequest<*>, headers: Map<String, String>): Single<String> {
        val effectiveContentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, DEFAULT_MEDIA_TYPE)
        request.contentType(effectiveContentType)
        headers.filter { it.key != HttpHeaders.CONTENT_TYPE }.forEach { request.header(it.key, it.value) }

        return Single.fromPublisher(client.retrieve(request, String::class.java))
    }
}

@Singleton
@Requires(bean = GenericWebhookClient::class)
class GenericWebhookService(
    private val client: GenericWebhookClient,
    private val messageFactory: WebhookMessageFactory,
) : TestableNotificationService<WebhookNotificationConfig> {

    private val logger = LoggerFactory.getLogger(this::class.java)

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
    )

    @Suppress("TooGenericExceptionCaught")
    fun sendWebhookEvent(target: WebhookNotificationConfig, event: MonitorEvent<out MonitorRecord>): Single<String> {
        val template = target.payloadTemplate
        val webhookUrl = target.url.toUri()
        val preparedHeaders = target.requestHeaders.orEmpty().mapValues { (_, value) ->
            messageFactory.fromMonitorEvent(event, value)
        }

        return if (template.isNullOrBlank()) {
            client.sendGenericMessage(
                webhookUrl = webhookUrl,
                message = messageFactory.fromMonitorEvent(event),
                headers = preparedHeaders,
            )
        } else {
            val payload = try {
                messageFactory.fromMonitorEvent(event, template)
            } catch (ex: Exception) {
                logger.error("Failed to parse webhook template: ${ex.message}")
                return Single.error(ex)
            }
            client.sendTemplatedMessage(
                webhookUrl = webhookUrl,
                payload = payload,
                headers = preparedHeaders,
            )
        }
    }

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
