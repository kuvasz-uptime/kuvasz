package com.kuvaszuptime.kuvasz.models.dto.monitor.stats

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

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
        description = "Number of monitors currently in progress (e.g., waiting for first check/heartbeat).",
        required = true
    )
    val inProgress: Int,
    @param:Schema(description = "Number of monitors currently under maintenance.", required = true)
    val inMaintenance: Int,
    @param:Schema(
        description = "The timestamp of the last incident detected across all monitors.",
        required = true,
        nullable = true,
    )
    val lastIncident: OffsetDateTime?,
)
