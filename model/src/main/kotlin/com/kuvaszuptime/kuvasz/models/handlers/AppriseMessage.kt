package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.annotation.Introspected

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AppriseMessage(
    val title: String,
    val body: String,
    val type: AppriseType,
    val format: AppriseFormat = AppriseFormat.TEXT,
    val tag: String? = null,
    val urls: String? = null,
)

@Introspected
enum class AppriseType(@field:JsonValue val value: String) {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    FAILURE("failure")
}

@Introspected
enum class AppriseFormat(@field:JsonValue val value: String) {
    TEXT("text")
}
