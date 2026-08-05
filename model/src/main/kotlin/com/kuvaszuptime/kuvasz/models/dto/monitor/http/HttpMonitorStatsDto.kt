package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class HttpMonitorStatsDto(
    @param:Schema(description = "Unique identifier of the monitor", required = true)
    val id: Long,
    @param:Schema(description = "Whether recording of latency is enabled for this monitor", required = true)
    val latencyHistoryEnabled: Boolean,
    @param:Schema(description = "Latency related statistics of the monitor in the given period", required = true)
    val latencyStats: LatencyStatsDto?,
    @param:Schema(description = "Uptime related statistics of the monitor in the given period", required = true)
    val uptimeHistory: HistoricalUptimeStatsDto,
    @param:Schema(description = "All the latency logs recorded for the monitor in the given period", required = true)
    val latencyLogs: List<LatencyLogDto>,
)

@Introspected
data class LatencyLogDto(
    @param:Schema(description = "Unique identifier of the latency log", required = true)
    val id: Long,
    @param:Schema(description = "The latency in milliseconds recorded for the monitor", required = true)
    val latencyInMs: Int,
    @param:Schema(description = "The timestamp when the latency was recorded", required = true)
    val createdAt: OffsetDateTime,
)
