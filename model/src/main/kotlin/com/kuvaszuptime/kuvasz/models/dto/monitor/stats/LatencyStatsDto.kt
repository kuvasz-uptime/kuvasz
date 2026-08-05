package com.kuvaszuptime.kuvasz.models.dto.monitor.stats

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class LatencyStatsDto(
    @param:Schema(description = "The average latency in milliseconds for the monitor", required = true)
    val averageLatencyInMs: Int?,
    @param:Schema(description = "The minimum latency in milliseconds for the monitor", required = true)
    val minLatencyInMs: Int?,
    @param:Schema(description = "The maximum latency in milliseconds for the monitor", required = true)
    val maxLatencyInMs: Int?,
    @param:Schema(description = "The 90th percentile latency in milliseconds for the monitor", required = true)
    val p90LatencyInMs: Int?,
    @param:Schema(description = "The 95th percentile latency in milliseconds for the monitor", required = true)
    val p95LatencyInMs: Int?,
    @param:Schema(description = "The 99th percentile latency in milliseconds for the monitor", required = true)
    val p99LatencyInMs: Int?,
)
