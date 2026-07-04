package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PushMonitoringStatsDto(
    @param:Schema(
        description = "The actual monitoring statistics for the current state of the monitors.",
        required = true
    )
    val actual: ActualMonitoringStats,
    @param:Schema(
        description = "The historical monitoring statistics, summarizing incidents and uptime ratios over time.",
        required = true
    )
    val history: HistoricalMonitoringStats,
) {
    data class ActualMonitoringStats(
        @param:Schema(description = "Statistics about the current state of uptime for all monitors.", required = true)
        val uptimeStats: ActualUptimeStats,
    )

    data class HistoricalMonitoringStats(
        @param:Schema(description = "Statistics about the historical uptime of all monitors.", required = true)
        val uptimeStats: HistoricalUptimeStatsDto,
    )
}
