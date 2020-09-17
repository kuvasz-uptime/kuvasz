package com.kuvaszuptime.kuvasz.config.handlers

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import javax.inject.Singleton
import javax.validation.constraints.NotBlank

@ConfigurationProperties("handler-config.pagerduty-event-handler")
@Singleton
@Introspected
class PagerdutyEventHandlerConfig {

    @NotBlank
    var integrationKey: String = ""
}
