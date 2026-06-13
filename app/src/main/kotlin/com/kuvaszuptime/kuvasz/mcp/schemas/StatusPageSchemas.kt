package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.WithLatency
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.LocalDate
import java.time.OffsetDateTime

@JsonSchema
@Introspected
data class StatusPageListSchema(val statusPages: List<StatusPageSchema>)

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatusPageSchema(
    val id: Long,
    val title: String,
    val slug: String,
    val public: Boolean,
    val monitorCount: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: StatusPageDto) = StatusPageSchema(
            id = dto.id,
            title = dto.title,
            slug = dto.slug,
            public = dto.public,
            monitorCount = dto.monitors.size,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatusPageDetailsSchema(
    val title: String,
    val systemStatus: SystemStatus,
    val generatedAt: OffsetDateTime,
    val monitors: List<StatusPageMonitorSchema>,
) {
    companion object {
        fun fromDto(dto: StatusPageDataDto) = StatusPageDetailsSchema(
            title = dto.title,
            systemStatus = dto.systemStatus,
            generatedAt = dto.generatedAt,
            monitors = dto.monitors.map { StatusPageMonitorSchema.fromDto(it) },
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatusPageMonitorSchema(
    val name: String,
    val type: String,
    val uptimeStatus: UptimeStatus?,
    val uptimeRatio: Double?,
    val lastCheck: OffsetDateTime?,
    val uptimeStatusHistory: List<StatusHistorySchema>,
    val averageLatencyInMs: Int?,
    val lastHeartbeat: OffsetDateTime?,
    val lastPacketLossPercentage: Int?,
) {
    companion object {
        fun fromDto(dto: StatusPageMonitorDetailsDto) = StatusPageMonitorSchema(
            name = dto.name,
            type = dto.type,
            uptimeStatus = dto.uptimeStatus,
            uptimeRatio = dto.uptimeRatio,
            lastCheck = dto.lastCheck,
            uptimeStatusHistory = dto.uptimeStatusHistory.map { StatusHistorySchema.fromDto(it) },
            averageLatencyInMs = if (dto is WithLatency) dto.averageLatencyInMs else null,
            lastHeartbeat = if (dto is StatusPagePushMonitorDetailsDto) dto.lastHeartbeat else null,
            lastPacketLossPercentage = if (dto is StatusPageIcmpMonitorDetailsDto) {
                dto.lastPacketLossPercentage
            } else null,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatusHistorySchema(
    val date: LocalDate,
    val outageCnt: Int?,
) {
    companion object {
        fun fromDto(dto: StatusHistoryDto) =
            StatusHistorySchema(date = dto.date, outageCnt = dto.outageCnt)
    }
}
