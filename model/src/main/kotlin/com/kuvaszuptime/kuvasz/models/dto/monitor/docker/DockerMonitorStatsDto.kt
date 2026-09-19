package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.OffsetDateTime

@Introspected
data class DockerMonitorStatsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = DockerMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = "Uptime related statistics of the monitor in the given period", required = true)
    val uptimeHistory: HistoricalUptimeStatsDto,
    @param:Schema(description = "CPU usage related statistics of the monitor in the given period", required = true)
    val cpuStats: CpuUsageStatsDto?,
    @param:Schema(description = "Memory usage related statistics of the monitor in the given period", required = true)
    val memoryStats: MemoryUsageStatsDto?,
    @param:Schema(
        description = "All the metrics logs recorded for the monitor in the given period",
        required = true,
    )
    val metricsLogs: List<DockerMetricsLogDto>,
)

@Introspected
data class DockerMetricsLogDto(
    @param:Schema(description = "Unique identifier of the metrics log", required = true)
    val id: Long,
    @param:Schema(
        description = "The CPU usage of the container in percent, null if the daemon reported no usable sample",
        required = true,
    )
    val cpuUsagePercent: BigDecimal?,
    @param:Schema(
        description = "The memory usage of the container in bytes, null if the daemon reported no usable sample",
        required = true,
    )
    val memoryUsageBytes: Long?,
    @param:Schema(
        description = "The memory limit of the container in bytes, null if the daemon reported no usable sample",
        required = true,
    )
    val memoryLimitBytes: Long?,
    @param:Schema(description = "The timestamp when the metrics were recorded", required = true)
    val createdAt: OffsetDateTime,
)

@Introspected
data class MemoryUsageStatsDto(
    @param:Schema(description = "The average memory usage of the container in bytes", required = true)
    val averageMemoryUsageBytes: Long?,
    @param:Schema(description = "The minimum memory usage of the container in bytes", required = true)
    val minMemoryUsageBytes: Long?,
    @param:Schema(description = "The maximum memory usage of the container in bytes", required = true)
    val maxMemoryUsageBytes: Long?,
)

@Introspected
data class CpuUsageStatsDto(
    @param:Schema(description = "The average CPU usage of the container", required = true)
    val averageCpuUsagePercentage: BigDecimal?,
    @param:Schema(description = "The minimum average CPU usage of the container", required = true)
    val minCpuUsagePercentage: BigDecimal?,
    @param:Schema(description = "The maximum average CPU usage of the container", required = true)
    val maxCpuUsagePercentage: BigDecimal?,
)
