package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@JsonSchema
@Introspected
data class IncidentListSchema(val incidents: List<IncidentSchema>)

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IncidentSchema(
    val monitorId: Long,
    val monitorName: String,
    val isMonitorEnabled: Boolean,
    val incidentType: IncidentType,
    val status: IncidentStatus,
    val details: String?,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: IncidentDto) = IncidentSchema(
            monitorId = dto.monitorId,
            monitorName = dto.monitorName,
            isMonitorEnabled = dto.isMonitorEnabled,
            incidentType = dto.incidentType,
            status = dto.status,
            details = dto.details,
            startedAt = dto.startedAt,
            endedAt = dto.endedAt,
            updatedAt = dto.updatedAt,
        )
    }
}
