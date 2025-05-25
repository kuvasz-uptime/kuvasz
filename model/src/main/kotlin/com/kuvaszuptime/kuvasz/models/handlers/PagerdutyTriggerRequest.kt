package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.annotation.Introspected

@Introspected
data class PagerdutyTriggerRequest(
    @field:JsonProperty("routing_key")
    val routingKey: String,
    @field:JsonProperty("event_action")
    val eventAction: PagerdutyEventAction = PagerdutyEventAction.TRIGGER,
    @field:JsonProperty("dedup_key")
    val dedupKey: String,
    val payload: PagerdutyTriggerPayload
)

@Introspected
enum class PagerdutyEventAction(@field:JsonValue val value: String) {
    TRIGGER("trigger"),
    RESOLVE("resolve")
}

@Introspected
data class PagerdutyTriggerPayload(
    val summary: String,
    val source: String,
    val severity: PagerdutySeverity
)

@Introspected
enum class PagerdutySeverity(@field:JsonValue val value: String) {
    CRITICAL("critical"),
    WARNING("warning")
}
