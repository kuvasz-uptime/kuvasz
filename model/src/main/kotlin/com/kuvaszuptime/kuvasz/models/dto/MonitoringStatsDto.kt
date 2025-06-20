package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class MonitoringStatsDto(
    val actual: ActualMonitoringStats,
    val history: HistoricalMonitoringStats,
) {
    data class ActualMonitoringStats(
        val uptimeStats: ActualUptimeStats,
        val sslStats: SslStats,
    ) {
        data class ActualUptimeStats(
            val total: Int,
            val down: Int,
            val up: Int,
            val paused: Int,
            val inProgress: Int,
            val lastIncident: OffsetDateTime?,
        )

        data class SslStats(
            val invalid: Int,
            val valid: Int,
            val willExpire: Int,
            val inProgress: Int,
        )
    }

    data class HistoricalMonitoringStats(
        val uptimeStats: HistoricalUptimeStats,
    ) {
        data class HistoricalUptimeStats(
            val incidents: Int,
            val affectedMonitors: Int,
            val uptimeRatio: Double?,
            val totalDowntimeSeconds: Long,
        )
    }
}
