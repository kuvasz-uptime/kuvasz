package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyLogDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMetricsLogDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HistoricalUptimeStatsSchema(
    val period: String,
    val incidents: Int,
    val affectedMonitors: Int,
    val uptimeRatio: Double?,
    val totalDowntimeSeconds: Long,
) {
    companion object {
        fun fromDto(dto: HistoricalUptimeStatsDto) = HistoricalUptimeStatsSchema(
            period = dto.period,
            incidents = dto.incidents,
            affectedMonitors = dto.affectedMonitors,
            uptimeRatio = dto.uptimeRatio,
            totalDowntimeSeconds = dto.totalDowntimeSeconds,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LatencyStatsSchema(
    val averageLatencyInMs: Int?,
    val minLatencyInMs: Int?,
    val maxLatencyInMs: Int?,
    val p90LatencyInMs: Int?,
    val p95LatencyInMs: Int?,
    val p99LatencyInMs: Int?,
) {
    companion object {
        fun fromDto(dto: LatencyStatsDto) = LatencyStatsSchema(
            averageLatencyInMs = dto.averageLatencyInMs,
            minLatencyInMs = dto.minLatencyInMs,
            maxLatencyInMs = dto.maxLatencyInMs,
            p90LatencyInMs = dto.p90LatencyInMs,
            p95LatencyInMs = dto.p95LatencyInMs,
            p99LatencyInMs = dto.p99LatencyInMs,
        )
    }
}

@Introspected
data class LatencyLogSchema(
    val id: Long,
    val latencyInMs: Int,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: LatencyLogDto) = LatencyLogSchema(
            id = dto.id,
            latencyInMs = dto.latencyInMs,
            createdAt = dto.createdAt,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PacketLossStatsSchema(
    val averagePacketLossPercentage: Int?,
    val minPacketLossPercentage: Int?,
    val maxPacketLossPercentage: Int?,
    val p90PacketLossPercentage: Int?,
    val p95PacketLossPercentage: Int?,
    val p99PacketLossPercentage: Int?,
) {
    companion object {
        fun fromDto(dto: PacketLossStatsDto) = PacketLossStatsSchema(
            averagePacketLossPercentage = dto.averagePacketLossPercentage,
            minPacketLossPercentage = dto.minPacketLossPercentage,
            maxPacketLossPercentage = dto.maxPacketLossPercentage,
            p90PacketLossPercentage = dto.p90PacketLossPercentage,
            p95PacketLossPercentage = dto.p95PacketLossPercentage,
            p99PacketLossPercentage = dto.p99PacketLossPercentage,
        )
    }
}

@JsonSchema
@Introspected
data class DeleteResultSchema(
    val deleted: Boolean,
    val id: Long,
)

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IcmpMetricsLogSchema(
    val id: Long,
    val latencyInMs: Int?,
    val packetLossPercentage: Int,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: IcmpMetricsLogDto) = IcmpMetricsLogSchema(
            id = dto.id,
            latencyInMs = dto.latencyInMs,
            packetLossPercentage = dto.packetLossPercentage,
            createdAt = dto.createdAt,
        )
    }
}
