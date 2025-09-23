package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration
import java.time.OffsetDateTime

@Introspected
data class HttpMonitoringStatsDto(
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
        @param:Schema(
            description = "Statistics about the current state of SSL certificates for all monitors.",
            required = true
        )
        val sslStats: SslStats,
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
                description = "Number of monitors currently in progress (e.g., waiting for first check).",
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

        data class SslStats(
            @param:Schema(
                description = " Number of SSL certificates that are currently invalid or expired.",
                required = true
            )
            val invalid: Int,
            @param:Schema(
                description = "Number of SSL certificates that are valid and not close to expiry.",
                required = true
            )
            val valid: Int,
            @param:Schema(
                description = "Number of SSL certificates that are close to expiry (within the threshold).",
                required = true
            )
            val willExpire: Int,
            @param:Schema(description = "Number of SSL certificates that has not been checked yet.", required = true)
            val inProgress: Int,
        )
    }

    data class HistoricalMonitoringStats(
        @param:Schema(description = "Statistics about the historical uptime of all monitors.", required = true)
        val uptimeStats: HistoricalUptimeStatsDto,
    )
}

data class HistoricalUptimeStatsDto(
    @param:Schema(
        implementation = Duration::class,
        description = "The period that was used for the calculation. An ISO-8601 Duration string.",
        required = true,
    )
    val period: String,
    @param:Schema(description = "Total number of incidents recorded across all monitors.", required = true)
    val incidents: Int,
    @param:Schema(
        description = "Total number of monitors that have been affected by incidents.",
        required = true
    )
    val affectedMonitors: Int,
    @param:Schema(
        description = "The percentage of uptime across all monitors.",
        required = true,
        nullable = true
    )
    val uptimeRatio: Double?,
    @param:Schema(description = "Total downtime in seconds across all monitors.", required = true)
    val totalDowntimeSeconds: Long,
)
