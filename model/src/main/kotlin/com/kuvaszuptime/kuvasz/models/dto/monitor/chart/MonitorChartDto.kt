package com.kuvaszuptime.kuvasz.models.dto.monitor.chart

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.CpuUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.MemoryUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal
import java.time.OffsetDateTime

@Introspected
data class LatencyChartDto(
    val latencyStats: LatencyStatsDto?,
    val points: List<LatencyChartPointDto>,
)

@Introspected
data class LatencyChartPointDto(
    val createdAt: OffsetDateTime,
    val latencyInMs: Int?,
    val maxLatencyInMs: Int?,
)

@Introspected
data class IcmpChartDto(
    val latencyStats: LatencyStatsDto?,
    val packetLossStats: PacketLossStatsDto?,
    val points: List<IcmpChartPointDto>,
)

@Introspected
data class IcmpChartPointDto(
    val createdAt: OffsetDateTime,
    val latencyInMs: Int?,
    val maxLatencyInMs: Int?,
    val packetLossPercentage: Int?,
    val maxPacketLossPercentage: Int?,
)

@Introspected
data class DockerChartDto(
    val cpuStats: CpuUsageStatsDto?,
    val memoryStats: MemoryUsageStatsDto?,
    val points: List<DockerChartPointDto>,
    val restarts: List<DockerRestartPointDto>,
)

@Introspected
data class DockerChartPointDto(
    val createdAt: OffsetDateTime,
    val cpuUsagePercent: BigDecimal?,
    val maxCpuUsagePercent: BigDecimal?,
    val memoryUsageBytes: Long?,
    val maxMemoryUsageBytes: Long?,
    val memoryLimitBytes: Long?,
)

/**
 * A check of a container whose restart count or creation time differs from the previous check that had a count. The
 * checks in between are left out, since they can't mark a restart, so the restarts are exact on any chart.
 */
@Introspected
data class DockerRestartPointDto(
    val createdAt: OffsetDateTime,
    val restartCount: Int,
    val containerCreatedAt: OffsetDateTime?,
)
