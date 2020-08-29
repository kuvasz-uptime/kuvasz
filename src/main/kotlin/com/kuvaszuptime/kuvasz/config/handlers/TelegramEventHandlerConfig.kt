package com.kuvaszuptime.kuvasz.config.handlers

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import javax.inject.Singleton

@ConfigurationProperties("handler-config.telegram-event-handler")
@Singleton
@Introspected
class TelegramEventHandlerConfig {
    var token: String? = null
}
