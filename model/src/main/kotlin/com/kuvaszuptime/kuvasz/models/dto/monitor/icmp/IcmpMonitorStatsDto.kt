package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class IcmpMonitorStatsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = IcmpMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = "Uptime related statistics of the monitor in the given period", required = true)
    val uptimeHistory: HistoricalUptimeStatsDto,
    @param:Schema(description = "Latency related statistics of the monitor in the given period", required = true)
    val latencyStats: LatencyStatsDto?,
    @param:Schema(description = "Packet loss related statistics of the monitor in the given period", required = true)
    val packetLossStats: PacketLossStatsDto?,
    @param:Schema(
        description = "All the latency and packet loss logs recorded for the monitor in the given period",
        required = true,
    )
    val metricsLogs: List<IcmpMetricsLogDto>,
)

@Introspected
data class IcmpMetricsLogDto(
    @param:Schema(description = "Unique identifier of the metrics log", required = true)
    val id: Long,
    @param:Schema(
        description = "The latency in milliseconds recorded for the monitor, null if the monitor was down",
        required = true
    )
    val latencyInMs: Int?,
    @param:Schema(description = "The packet loss percentage recorded for the monitor", required = true)
    val packetLossPercentage: Int,
    @param:Schema(description = "The timestamp when the metrics were recorded", required = true)
    val createdAt: OffsetDateTime,
)

@Introspected
data class PacketLossStatsDto(
    @param:Schema(description = "The average packet loss percentage for the monitor", required = true)
    val averagePacketLossPercentage: Int?,
    @param:Schema(description = "The minimum packet loss percentage for the monitor", required = true)
    val minPacketLossPercentage: Int?,
    @param:Schema(description = "The maximum packet loss percentage for the monitor", required = true)
    val maxPacketLossPercentage: Int?,
    @param:Schema(description = "The 90th percentile packet loss percentage for the monitor", required = true)
    val p90PacketLossPercentage: Int?,
    @param:Schema(description = "The 95th percentile packet loss percentage for the monitor", required = true)
    val p95PacketLossPercentage: Int?,
    @param:Schema(description = "The 99th percentile packet loss percentage for the monitor", required = true)
    val p99PacketLossPercentage: Int?,
)
