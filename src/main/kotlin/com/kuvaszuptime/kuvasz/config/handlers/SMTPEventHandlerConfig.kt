package com.kuvaszuptime.kuvasz.config.handlers

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank

@ConfigurationProperties("handler-config.smtp-event-handler")
@Singleton
@Introspected
class SMTPEventHandlerConfig : EmailEventHandlerConfig {
    @NotBlank
    @Email
    override var from: String = ""

    @NotBlank
    @Email
    override var to: String = ""
}
