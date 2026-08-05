package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected

const val P90 = .90
const val P95 = .95
const val P99 = .99

@Introspected
data class LatencyMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val min: Int?,
    val max: Int?,
    val p90: Int?,
    val p95: Int?,
    val p99: Int?,
)

fun LatencyMetricResult.toStatsDto() = LatencyStatsDto(
    averageLatencyInMs = avg,
    minLatencyInMs = min,
    maxLatencyInMs = max,
    p90LatencyInMs = p90,
    p95LatencyInMs = p95,
    p99LatencyInMs = p99,
)

fun PacketLossMetricResult.toStatsDto() = PacketLossStatsDto(
    averagePacketLossPercentage = avg,
    minPacketLossPercentage = min,
    maxPacketLossPercentage = max,
    p90PacketLossPercentage = p90,
    p95PacketLossPercentage = p95,
    p99PacketLossPercentage = p99,
)
