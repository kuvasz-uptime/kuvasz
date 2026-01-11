package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookMonitorStatus
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import java.net.URI
import java.time.Instant

@Singleton
@Requires(property = WebhookNotificationConfig.CONFIG_PREFIX)
class GenericWebhookClient(@param:Client private val client: HttpClient) {

    // TODO
    @Retryable
    fun sendMessage(webhookUrl: URI, message: GenericWebhookMessage, headers: Map<String, String>): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        headers.forEach { req.header(it.key, it.value) }

        return Single.fromPublisher(client.retrieve(req, String::class.java))
    }
}

@Singleton
@Requires(bean = GenericWebhookClient::class)
class GenericWebhookService(private val client: GenericWebhookClient) :
    TestableNotificationService<WebhookNotificationConfig> {

    // TODO
    fun sendWebhookEvent(integrationConfig: IntegrationConfig, message: GenericWebhookMessage): Single<String> {
        val config = integrationConfig as WebhookNotificationConfig
        val webhookUrl = config.url.toUri()

        return client.sendMessage(webhookUrl, message, config.requestHeaders.orEmpty())
    }

    // TODO send every possible message types if custom templates are present
    override fun sendTestMessage(integrationConfig: WebhookNotificationConfig): Single<NotificationTestResult> =
        sendWebhookEvent(integrationConfig, createTestMessage()).toNotificationTestResult()

    private fun createTestMessage() = GenericWebhookMessage(
        deduplicationKey = "test_dedup_key_1",
        monitorId = MonitorID(MonitorType.HTTP_SSL, "test"),
        monitorName = "test",
        timestamp = Instant.now().toEpochMilli(),
        status = WebhookMonitorStatus.HTTP_DOWN,
        eventDetails = "A simulated error occurred during test execution"
    )
}
