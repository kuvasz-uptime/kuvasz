package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected

const val P90 = .90
const val P95 = .95
const val P99 = .99

/**
 * The shape every aggregated measurement of a monitor shares, so the aggregation itself can be written once, against
 * any of the metrics log tables. Also keeps the column aliases of that query refactor-safe.
 */
interface MetricResult {
    val monitorId: Long
    val avg: Int?
    val min: Int?
    val max: Int?
    val p90: Int?
    val p95: Int?
    val p99: Int?
}

@Introspected
data class LatencyMetricResult(
    override val monitorId: Long,
    override val avg: Int?,
    override val min: Int?,
    override val max: Int?,
    override val p90: Int?,
    override val p95: Int?,
    override val p99: Int?,
) : MetricResult

@Introspected
data class PacketLossMetricResult(
    override val monitorId: Long,
    override val avg: Int?,
    override val min: Int?,
    override val max: Int?,
    override val p90: Int?,
    override val p95: Int?,
    override val p99: Int?,
) : MetricResult

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
