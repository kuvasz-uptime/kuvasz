package com.kuvaszuptime.kuvasz.models.dto.event

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class HttpUptimeEventDto(
    @param:Schema(description = "Unique identifier for the uptime event", required = true)
    val id: Long,
    @param:Schema(description = "The status of the uptime event", required = true)
    val status: UptimeStatus,
    @param:Schema(
        description = "The error that occurred during the uptime check, if any",
        required = true,
        nullable = true
    )
    val error: String?,
    @param:Schema(description = "The timestamp when the uptime event started", required = true)
    val startedAt: OffsetDateTime,
    @param:Schema(
        description = "The timestamp when the uptime event ended, if applicable",
        required = true,
        nullable = true
    )
    val endedAt: OffsetDateTime?,
    @param:Schema(description = "The timestamp when the uptime event was updated", required = true)
    val updatedAt: OffsetDateTime
)
