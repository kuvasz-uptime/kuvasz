package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.CpuUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.MemoryUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal

const val P90 = .90
const val P95 = .95
const val P99 = .99

/**
 * The shape every aggregated measurement of a monitor shares, so the aggregation itself can be written once, against
 * any of the metrics log tables. Also keeps the column aliases of that query refactor-safe.
 *
 * It is generic over [N] because the measurements are not all counted in the same units: a latency or a packet loss
 * percentage fits an `Int`, a memory reading in bytes needs a `Long`, and a CPU percentage would lose everything
 * below a whole percent if it were not kept fractional.
 */
interface MetricResult<N : Number> {
    val monitorId: Long
    val avg: N?
    val min: N?
    val max: N?
}

/**
 * The measurements that are also worth reading by percentile - the ones where the tail is the interesting part,
 * which is to say the timings and the loss ratios. A container's CPU and memory are deliberately not among them:
 * their spread over a period says little that the average and the peak do not already.
 */
interface PercentileMetricResult<N : Number> : MetricResult<N> {
    val p90: N?
    val p95: N?
    val p99: N?
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
) : PercentileMetricResult<Int>

@Introspected
data class PacketLossMetricResult(
    override val monitorId: Long,
    override val avg: Int?,
    override val min: Int?,
    override val max: Int?,
    override val p90: Int?,
    override val p95: Int?,
    override val p99: Int?,
) : PercentileMetricResult<Int>

@Introspected
data class CpuUsageMetricResult(
    override val monitorId: Long,
    override val avg: BigDecimal?,
    override val min: BigDecimal?,
    override val max: BigDecimal?,
) : MetricResult<BigDecimal>

@Introspected
data class MemoryUsageMetricResult(
    override val monitorId: Long,
    override val avg: Long?,
    override val min: Long?,
    override val max: Long?,
) : MetricResult<Long>

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

fun CpuUsageMetricResult.toStatsDto() = CpuUsageStatsDto(
    averageCpuUsagePercentage = avg,
    minCpuUsagePercentage = min,
    maxCpuUsagePercentage = max,
)

fun MemoryUsageMetricResult.toStatsDto() = MemoryUsageStatsDto(
    averageMemoryUsageBytes = avg,
    minMemoryUsageBytes = min,
    maxMemoryUsageBytes = max,
)
