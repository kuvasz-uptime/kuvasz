package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class DnsMonitorStatsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = DnsMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = "Uptime related statistics of the monitor in the given period", required = true)
    val uptimeHistory: HistoricalUptimeStatsDto,
    @param:Schema(description = "Latency related statistics of the monitor in the given period", required = true)
    val latencyStats: LatencyStatsDto?,
    @param:Schema(
        description = "All the latency logs recorded for the monitor in the given period",
        required = true,
    )
    val metricsLogs: List<DnsMetricsLogDto>,
)

@Introspected
data class DnsMetricsLogDto(
    @param:Schema(description = "Unique identifier of the metrics log", required = true)
    val id: Long,
    @param:Schema(
        description = "The resolution latency in milliseconds recorded for the monitor, null if the monitor was down",
        required = true
    )
    val latencyInMs: Int?,
    @param:Schema(description = "The timestamp when the metrics were recorded", required = true)
    val createdAt: OffsetDateTime,
)
