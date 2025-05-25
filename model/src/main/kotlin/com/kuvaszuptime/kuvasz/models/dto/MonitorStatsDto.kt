package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class MonitorStatsDto(
    val id: Long,
    val latencyHistoryEnabled: Boolean,
    val averageLatencyInMs: Int?,
    val p95LatencyInMs: Int?,
    val p99LatencyInMs: Int?,
    val latencyLogs: List<LatencyLogDto>,
)

@Introspected
data class LatencyLogDto(
    val id: Long,
    val latencyInMs: Int,
    val createdAt: OffsetDateTime,
)
