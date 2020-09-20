package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.handlers.SlackWebhookMessage
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single
import javax.inject.Singleton

@Client("\${handler-config.slack-event-handler.webhook-url}")
interface SlackWebhookClient {

    @Post("/")
    fun sendMessage(@Body message: SlackWebhookMessage): Single<String>
}

@Singleton
@Requires(property = "handler-config.slack-event-handler.enabled", value = "true")
class SlackWebhookService(private val client: SlackWebhookClient) : TextMessageService {

    companion object {
        private const val RETRY_COUNT = 3L
    }

    override fun sendMessage(content: String): Single<String> =
        client
            .sendMessage(SlackWebhookMessage(text = content))
            .retry(RETRY_COUNT)
}
