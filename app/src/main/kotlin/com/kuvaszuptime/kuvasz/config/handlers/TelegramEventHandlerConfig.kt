package com.kuvaszuptime.kuvasz.config.handlers

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@ConfigurationProperties("handler-config.telegram-event-handler")
@Singleton
@Introspected
class TelegramEventHandlerConfig {

    @NotBlank
    var token: String = ""

    @NotBlank
    var chatId: String = ""
}
