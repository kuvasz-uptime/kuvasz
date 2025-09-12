package com.kuvaszuptime.kuvasz.models.dto.incident

import com.kuvaszuptime.kuvasz.models.IncidentType
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class IncidentDto(
    @param:Schema(description = "ID of the monitor this incident belongs to", required = true)
    val monitorId: Long,
    @param:Schema(description = "Name of the monitor this incident belongs to", required = true)
    val monitorName: String,
    @param:Schema(description = "Whether the monitor related to the incident is enabled", required = true)
    val isMonitorEnabled: Boolean,
    @param:Schema(description = "Type of the incident", required = true)
    val incidentType: IncidentType,
    @param:Schema(description = "Status of the incident", required = true)
    val status: IncidentStatus,
    @param:Schema(description = "Details about the incident, e.g. error message", required = true, nullable = true)
    val details: String?,
    @param:Schema(description = "When the incident started", required = true)
    val startedAt: OffsetDateTime,
    @param:Schema(description = "When the incident ended, null if ongoing", required = true, nullable = true)
    val endedAt: OffsetDateTime?,
    @param:Schema(description = "When the incident was last updated", required = true)
    val updatedAt: OffsetDateTime,
)

@Introspected
enum class IncidentStatus {
    ONGOING,
    RESOLVED,
}
