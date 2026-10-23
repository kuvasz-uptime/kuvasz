package com.kuvaszuptime.kuvasz.models.dto.monitor.chart

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.CpuUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.MemoryUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.LatencyStatsDto
import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal

// The checks of the period are grouped into buckets, so a chart has a bounded number of points (see
// MonitorChartService). The series are sent as one array per column, the same index of the arrays belongs to the same
// bucket, the oldest first. The timestamp of a bucket is the average time of its checks in epoch milliseconds, `checks`
// is the number of the checks in it, and `checksWithoutReading` the number of those that have no reading of the
// measurement the chart is about (e.g. the failed ones), which the average, the lowest and the highest readings leave
// out. They are null for a bucket that has no reading at all.

@Introspected
data class LatencyChartDto(
    val latencyStats: LatencyStatsDto?,
    val series: LatencySeriesDto,
    val incidents: List<ChartIncidentDto>,
) {
    companion object {
        fun empty() = LatencyChartDto(latencyStats = null, series = LatencySeriesDto.empty(), incidents = emptyList())
    }
}

@Introspected
data class LatencySeriesDto(
    val timestamps: List<Long>,
    val checks: List<Int>,
    val checksWithoutReading: List<Int>,
    val latencies: List<Int?>,
    val lowestLatencies: List<Int?>,
    val highestLatencies: List<Int?>,
) {
    companion object {
        fun empty() = LatencySeriesDto(
            timestamps = emptyList(),
            checks = emptyList(),
            checksWithoutReading = emptyList(),
            latencies = emptyList(),
            lowestLatencies = emptyList(),
            highestLatencies = emptyList(),
        )
    }
}

@Introspected
data class IcmpChartDto(
    val latencyStats: LatencyStatsDto?,
    val packetLossStats: PacketLossStatsDto?,
    val series: IcmpSeriesDto,
    val incidents: List<ChartIncidentDto>,
) {
    companion object {
        fun empty() = IcmpChartDto(
            latencyStats = null,
            packetLossStats = null,
            series = IcmpSeriesDto.empty(),
            incidents = emptyList(),
        )
    }
}

@Introspected
data class IcmpSeriesDto(
    val timestamps: List<Long>,
    val checks: List<Int>,
    val checksWithoutReading: List<Int>,
    val latencies: List<Int?>,
    val lowestLatencies: List<Int?>,
    val highestLatencies: List<Int?>,
    val packetLossPercentages: List<Int?>,
    val lowestPacketLossPercentages: List<Int?>,
    val highestPacketLossPercentages: List<Int?>,
) {
    companion object {
        fun empty() = IcmpSeriesDto(
            timestamps = emptyList(),
            checks = emptyList(),
            checksWithoutReading = emptyList(),
            latencies = emptyList(),
            lowestLatencies = emptyList(),
            highestLatencies = emptyList(),
            packetLossPercentages = emptyList(),
            lowestPacketLossPercentages = emptyList(),
            highestPacketLossPercentages = emptyList(),
        )
    }
}

@Introspected
data class DockerChartDto(
    val cpuStats: CpuUsageStatsDto?,
    val memoryStats: MemoryUsageStatsDto?,
    val series: DockerSeriesDto,
    val restarts: List<DockerRestartPointDto>,
    val incidents: List<ChartIncidentDto>,
) {
    companion object {
        fun empty() = DockerChartDto(
            cpuStats = null,
            memoryStats = null,
            series = DockerSeriesDto.empty(),
            restarts = emptyList(),
            incidents = emptyList(),
        )
    }
}

@Introspected
data class DockerSeriesDto(
    val timestamps: List<Long>,
    val checks: List<Int>,
    val checksWithoutReading: List<Int>,
    val cpuUsagePercentages: List<BigDecimal?>,
    val lowestCpuUsagePercentages: List<BigDecimal?>,
    val highestCpuUsagePercentages: List<BigDecimal?>,
    val memoryUsageBytes: List<Long?>,
    val lowestMemoryUsageBytes: List<Long?>,
    val highestMemoryUsageBytes: List<Long?>,
    val memoryLimitBytes: List<Long?>,
) {
    companion object {
        fun empty() = DockerSeriesDto(
            timestamps = emptyList(),
            checks = emptyList(),
            checksWithoutReading = emptyList(),
            cpuUsagePercentages = emptyList(),
            lowestCpuUsagePercentages = emptyList(),
            highestCpuUsagePercentages = emptyList(),
            memoryUsageBytes = emptyList(),
            lowestMemoryUsageBytes = emptyList(),
            highestMemoryUsageBytes = emptyList(),
            memoryLimitBytes = emptyList(),
        )
    }
}

/**
 * A check of a container whose restart count or creation time differs from the previous check that had a count, or the
 * baseline of the next one: the last check with a count before the period, or without one, the first one of the
 * period. The checks in between can't mark a restart, so they are left out. The times are epoch milliseconds, like the
 * timestamps of the series.
 */
@Introspected
data class DockerRestartPointDto(
    val timestamp: Long,
    val restartCount: Int,
    val containerCreatedAt: Long?,
)

/**
 * An incident of the monitor that was open at any point of the period, its times in epoch milliseconds.
 */
@Introspected
data class ChartIncidentDto(
    val startedAt: Long,
    val endedAt: Long?,
    val details: String?,
)
