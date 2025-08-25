package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class HttpMonitorStatsDto(
    @param:Schema(description = "Unique identifier of the monitor", required = true)
    val id: Long,
    @param:Schema(description = "Whether recording of latency is enabled for this monitor", required = true)
    val latencyHistoryEnabled: Boolean,
    @param:Schema(description = "The average latency in milliseconds for the monitor")
    val averageLatencyInMs: Int?,
    @param:Schema(description = "The minimum latency in milliseconds for the monitor")
    val minLatencyInMs: Int?,
    @param:Schema(description = "The maximum latency in milliseconds for the monitor")
    val maxLatencyInMs: Int?,
    @param:Schema(description = "The 90th percentile latency in milliseconds for the monitor")
    val p90LatencyInMs: Int?,
    @param:Schema(description = "The 95th percentile latency in milliseconds for the monitor")
    val p95LatencyInMs: Int?,
    @param:Schema(description = "The 99th percentile latency in milliseconds for the monitor")
    val p99LatencyInMs: Int?,
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
