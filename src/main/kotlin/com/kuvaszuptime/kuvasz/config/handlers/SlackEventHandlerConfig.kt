package com.kuvaszuptime.kuvasz.config.handlers

import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import javax.inject.Singleton
import javax.validation.constraints.Pattern

@ConfigurationProperties("handler-config.slack-event-handler")
@Singleton
@Introspected
class SlackEventHandlerConfig {
    @Pattern(regexp = URI_REGEX)
    var webhookUrl: String? = null
}
