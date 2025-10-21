package com.kuvaszuptime.kuvasz.models.dto.monitor.stats

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration

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
