package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.handlers.PagerdutyEventHandlerConfig
import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

@Singleton
@Requires(property = "handler-config.pagerduty-event-handler.enabled", value = "true")
class PagerdutyAPIService(
    val config: PagerdutyEventHandlerConfig
)
