package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
data class PagerdutyResolveRequest(
    @field:JsonProperty("routing_key")
    val routingKey: String,
    @field:JsonProperty("event_action")
    val eventAction: PagerdutyEventAction = PagerdutyEventAction.RESOLVE,
    @field:JsonProperty("dedup_key")
    val dedupKey: String
)
