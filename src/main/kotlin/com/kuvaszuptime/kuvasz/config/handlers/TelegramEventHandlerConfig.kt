package com.kuvaszuptime.kuvasz.config.handlers

import com.kuvaszuptime.kuvasz.models.dto.Validation
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import javax.inject.Singleton
import javax.validation.constraints.Pattern

@ConfigurationProperties("handler-config.telegram-event-handler")
@Singleton
@Introspected
class TelegramEventHandlerConfig {
    var token: String? = null;
}
