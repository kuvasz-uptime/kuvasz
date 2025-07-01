package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class MonitoringStatsDto(
    @Schema(description = "The actual monitoring statistics for the current state of the monitors.", required = true)
    val actual: ActualMonitoringStats,
    @Schema(
        description = "The historical monitoring statistics, summarizing incidents and uptime ratios over time.",
        required = true
    )
    val history: HistoricalMonitoringStats,
) {
    data class ActualMonitoringStats(
        @Schema(description = "Statistics about the current state of uptime for all monitors.", required = true)
        val uptimeStats: ActualUptimeStats,
        @Schema(
            description = "Statistics about the current state of SSL certificates for all monitors.",
            required = true
        )
        val sslStats: SslStats,
    ) {
        data class ActualUptimeStats(
            @Schema(description = "Total number of monitors currently being monitored.", required = true)
            val total: Int,
            @Schema(description = "Number of monitors currently down.", required = true)
            val down: Int,
            @Schema(description = "Number of monitors currently up.", required = true)
            val up: Int,
            @Schema(description = "Number of monitors currently paused.", required = true)
            val paused: Int,
            @Schema(
                description = "Number of monitors currently in progress (e.g., waiting for first check).",
                required = true
            )
            val inProgress: Int,
            @Schema(
                description = "The timestamp of the last incident detected across all monitors.",
                required = true,
                nullable = true,
            )
            val lastIncident: OffsetDateTime?,
        )

        data class SslStats(
            @Schema(description = " Number of SSL certificates that are currently invalid or expired.", required = true)
            val invalid: Int,
            @Schema(description = "Number of SSL certificates that are valid and not close to expiry.", required = true)
            val valid: Int,
            @Schema(
                description = "Number of SSL certificates that are close to expiry (within the threshold).",
                required = true
            )
            val willExpire: Int,
            @Schema(description = "Number of SSL certificates that has not been checked yet.", required = true)
            val inProgress: Int,
        )
    }

    data class HistoricalMonitoringStats(
        @Schema(description = "Statistics about the historical uptime of all monitors.", required = true)
        val uptimeStats: HistoricalUptimeStats,
    ) {
        data class HistoricalUptimeStats(
            @Schema(description = "Total number of incidents recorded across all monitors.", required = true)
            val incidents: Int,
            @Schema(description = "Total number of monitors that have been affected by incidents.", required = true)
            val affectedMonitors: Int,
            @Schema(description = "The percentage of uptime across all monitors.", required = true, nullable = true)
            val uptimeRatio: Double?,
            @Schema(description = "Total downtime in seconds across all monitors.", required = true)
            val totalDowntimeSeconds: Long,
        )
    }
}
