package com.kuvaszuptime.kuvasz.models.dto.event

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class IcmpUptimeEventDto(
    @param:Schema(description = UptimeEventDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = UptimeEventDocs.UPTIME_STATUS, required = true)
    val status: UptimeStatus,
    @param:Schema(description = UptimeEventDocs.ERROR, required = true, nullable = true)
    val error: String?,
    @param:Schema(description = UptimeEventDocs.STARTED_AT, required = true)
    val startedAt: OffsetDateTime,
    @param:Schema(description = UptimeEventDocs.ENDED_AT, required = true, nullable = true)
    val endedAt: OffsetDateTime?,
    @param:Schema(description = UptimeEventDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
)
