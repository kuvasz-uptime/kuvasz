package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackWebhookMessage
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
@Requires(property = SlackNotificationConfig.CONFIG_PREFIX)
class SlackWebhookClient(@Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(webhookUrl: URI, message: SlackWebhookMessage): Single<String> {
        val req = HttpRequest.POST(webhookUrl, message)
        return Single.fromPublisher(client.retrieve(req, String::class.java))
    }
}

@Singleton
@Requires(bean = SlackWebhookClient::class)
class SlackWebhookService(private val client: SlackWebhookClient) : TextMessageService {

    override fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String> {
        val webhookUrl = (integrationConfig as SlackNotificationConfig).webhookUrl.toUri()
        return client.sendMessage(webhookUrl, SlackWebhookMessage(text = content))
    }
}
