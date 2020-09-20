package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.handlers.TelegramEventHandlerConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramAPIMessage
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable
import io.reactivex.Single
import javax.inject.Singleton

@Client("https://api.telegram.org/bot\${handler-config.telegram-event-handler.token}")
@Retryable
interface TelegramAPIClient {

    @Post("/sendMessage")
    fun sendMessage(@Body message: TelegramAPIMessage): Single<String>
}

@Singleton
@Requires(property = "handler-config.telegram-event-handler.enabled", value = "true")
class TelegramAPIService(
    private val telegramEventHandlerConfig: TelegramEventHandlerConfig,
    private val client: TelegramAPIClient
) : TextMessageService {
    override fun sendMessage(content: String): Single<String> =
        client.sendMessage(TelegramAPIMessage(chat_id = telegramEventHandlerConfig.chatId, text = content))
}
