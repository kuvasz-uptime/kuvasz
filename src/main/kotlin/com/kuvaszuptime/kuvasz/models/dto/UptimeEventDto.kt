package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class UptimeEventDto(
    val status: UptimeStatus,
    val error: String?,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime
)
