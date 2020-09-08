package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TelegramAPIService
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory

@Context
@Requires(property = "handler-config.telegram-event-handler.enabled", value = "true")
class TelegramEventHandler(
    telegramAPIService: TelegramAPIService,
    eventDispatcher: EventDispatcher
) : TextMessageEventHandler(eventDispatcher, telegramAPIService) {

    override val logger = LoggerFactory.getLogger(TelegramEventHandler::class.java)

    override fun String.bold(): String = "<b>$this</b>"

    override fun String.italic(): String = "<i>$this</i>"
}
