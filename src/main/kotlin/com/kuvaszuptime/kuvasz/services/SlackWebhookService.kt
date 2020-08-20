package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.handlers.SlackEventHandlerConfig
import com.kuvaszuptime.kuvasz.models.SlackWebhookMessage
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.event.annotation.EventListener
import io.reactivex.Flowable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Requires(property = "handler-config.slack-event-handler.enabled", value = "true")
class SlackWebhookService @Inject constructor(
    private val slackEventHandlerConfig: SlackEventHandlerConfig,
    private val httpClient: RxHttpClient
) {

    companion object {
        private const val RETRY_COUNT = 3L
    }

    fun sendMessage(message: SlackWebhookMessage): Flowable<HttpResponse<String>> {
        val request: HttpRequest<SlackWebhookMessage> = HttpRequest.POST(slackEventHandlerConfig.webhookUrl, message)

        return httpClient
            .exchange(request, Argument.STRING, Argument.STRING)
            .retry(RETRY_COUNT)
    }

    @EventListener
    @Suppress("UNUSED_PARAMETER")
    internal fun onShutdownEvent(event: ShutdownEvent) {
        httpClient.close()
    }
}
