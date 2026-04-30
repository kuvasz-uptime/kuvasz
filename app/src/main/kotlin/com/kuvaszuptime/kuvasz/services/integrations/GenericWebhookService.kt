package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.kotlin.http.bodyOrNull
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant

@Singleton
@Requires(property = WebhookNotificationConfig.CONFIG_PREFIX)
class GenericWebhookClient(@param:Client private val client: HttpClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // TODO ignore HTTP errors completely, only log a warning
    // TODO test correct content-type header in different scenarios
    @Retryable
    fun sendMessage(webhookUrl: URI, message: GenericWebhookMessage, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        val effectiveContentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        req.contentType(effectiveContentType)
        headers.filter { it.key != HttpHeaders.CONTENT_TYPE }.forEach { req.header(it.key, it.value) }
        // TODO remove logging
        logger.info(
            "Sending message to $webhookUrl with headers ${
                req.headers.joinToString { header ->
                    header.key + ":" + header.value.joinToString(
                        ";"
                    )
                }
            }: ${req.bodyOrNull}"
        )

        return Single.fromPublisher(client.retrieve(req, String::class.java))
    }

    @Retryable
    fun sendMessage(webhookUrl: URI, payload: String, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, payload)
        val effectiveContentType = headers.getOrDefault(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        req.contentType(effectiveContentType)
        headers.filter { it.key != HttpHeaders.CONTENT_TYPE }.forEach { req.header(it.key, it.value) }
        // TODO remove logging
        logger.info(
            "Sending message to $webhookUrl with headers ${
                req.headers.joinToString { header ->
                    header.key + ":" + header.value.joinToString(
                        ";"
                    )
                }
            }: ${req.bodyOrNull}"
        )

        return Single.fromPublisher(client.retrieve(req, String::class.java))
    }
}

@Singleton
@Requires(bean = GenericWebhookClient::class)
class GenericWebhookService(private val client: GenericWebhookClient) :
    TestableNotificationService<WebhookNotificationConfig> {

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

    // TODO decide on which type of message to send as a test
    override fun sendTestMessage(integrationConfig: WebhookNotificationConfig): Single<NotificationTestResult> =
        sendGenericWebhookEvent(integrationConfig, createTestMessage()).toNotificationTestResult()

    private fun createTestMessage() = GenericWebhookMessage(
        deduplicationKey = "test_dedup_key_1",
        monitorId = MonitorID(MonitorType.HTTP_SSL, "test"),
        monitorName = "test",
        timestamp = Instant.now().toEpochMilli(),
        type = WebhookEventType.HTTP_DOWN,
        eventDetails = "A simulated error occurred during test execution"
    )
}
