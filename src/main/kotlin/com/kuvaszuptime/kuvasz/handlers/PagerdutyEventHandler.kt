package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.services.PagerdutyAPIService
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(property = "handler-config.pagerduty-event-handler.enabled", value = "true")
class PagerdutyEventHandler(
    val pagerdutyAPIService: PagerdutyAPIService
)
