package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.DiscordWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton
import java.net.URI

@Singleton
@Requires(property = DiscordNotificationConfig.CONFIG_PREFIX)
class DiscordWebhookClient(@Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(webhookUrl: URI, message: DiscordWebhookMessage): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        return Single.fromPublisher(client.exchange(req)).map { "OK" }
    }
}

@Singleton
@Requires(bean = DiscordWebhookClient::class)
class DiscordWebhookService(private val client: DiscordWebhookClient) :
    TextMessageService,
    TestableNotificationService<DiscordNotificationConfig> {

    override fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String> {
        val webhookUrl = (integrationConfig as DiscordNotificationConfig).webhookUrl.toUri()
        return client.sendMessage(webhookUrl, DiscordWebhookMessage(content = content))
    }

    override fun sendTestMessage(integrationConfig: DiscordNotificationConfig): Single<NotificationTestResult> =
        sendMessage(integrationConfig, Messages.integrationTestMessage()).toNotificationTestResult()
}
