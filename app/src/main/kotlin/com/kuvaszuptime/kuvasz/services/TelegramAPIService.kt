package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramAPIMessage
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Singleton

@Singleton
@Requires(property = TelegramNotificationConfig.CONFIG_PREFIX)
class TelegramAPIClient(@Client private val client: HttpClient) {

    @Retryable
    fun sendMessage(apiToken: String, message: TelegramAPIMessage): Single<String> {
        val uri = "https://api.telegram.org/bot$apiToken/sendMessage".toUri()
        val req = HttpRequest.POST(uri, message)

        return Single.fromPublisher(client.retrieve(req, String::class.java))
    }
}

@Singleton
class TelegramAPIService(
    private val client: TelegramAPIClient,
) : TextMessageService {
    override fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String> =
        (integrationConfig as? TelegramNotificationConfig)?.let { telegramConfig ->
            client.sendMessage(
                apiToken = telegramConfig.apiToken,
                message = TelegramAPIMessage(chatId = telegramConfig.chatId, text = content),
            )
        } ?: Single.error(
            TelegramConfigurationException("Invalid integration configuration for Telegram: $integrationConfig")
        )
}

class TelegramConfigurationException(override val message: String?) : Exception(message)
