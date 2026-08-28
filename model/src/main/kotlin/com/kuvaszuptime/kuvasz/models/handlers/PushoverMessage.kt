package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.annotation.Introspected

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PushoverMessage(
    val title: String,
    val message: String,
    val priority: PushoverPriority,
    val token: String? = null,
    val user: String? = null,
    val device: String? = null,
    val sound: String? = null,
    val retry: Int? = null,
    val expire: Int? = null,
    val tags: String? = null,
)

@Introspected
data class PushoverCancelRequest(
    val token: String,
)

@Introspected
enum class PushoverPriority(@field:JsonValue val value: Int) {
    NORMAL(0),
    HIGH(1),
    EMERGENCY(2),
}
