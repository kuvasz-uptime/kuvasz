package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

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
