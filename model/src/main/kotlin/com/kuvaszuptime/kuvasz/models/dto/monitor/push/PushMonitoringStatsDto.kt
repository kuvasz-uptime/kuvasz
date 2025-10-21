package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

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
    ) {
        data class ActualUptimeStats(
            @param:Schema(description = "Total number of monitors currently being monitored.", required = true)
            val total: Int,
            @param:Schema(description = "Number of monitors currently down.", required = true)
            val down: Int,
            @param:Schema(description = "Number of monitors currently up.", required = true)
            val up: Int,
            @param:Schema(description = "Number of monitors currently paused.", required = true)
            val paused: Int,
            @param:Schema(
                description = "Number of monitors currently in progress (e.g., waiting for first heartbeat).",
                required = true
            )
            val inProgress: Int,
            @param:Schema(
                description = "The timestamp of the last incident detected across all monitors.",
                required = true,
                nullable = true,
            )
            val lastIncident: OffsetDateTime?,
        )
    }

    data class HistoricalMonitoringStats(
        @param:Schema(description = "Statistics about the historical uptime of all monitors.", required = true)
        val uptimeStats: HistoricalUptimeStatsDto,
    )
}
