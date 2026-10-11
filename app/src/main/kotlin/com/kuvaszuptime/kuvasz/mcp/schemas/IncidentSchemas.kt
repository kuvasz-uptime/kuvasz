package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.NotBlank
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
    val eventId: Long,
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
            eventId = dto.eventId,
            status = dto.status,
            details = dto.details,
            startedAt = dto.startedAt,
            endedAt = dto.endedAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IncidentNoteSchema(
    val id: Long,
    val status: InvestigationStatus,
    val content: String,
    val timestamp: OffsetDateTime?,
    val public: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: IncidentNoteDto) = IncidentNoteSchema(
            id = dto.id,
            status = dto.status,
            content = dto.content,
            timestamp = dto.timestamp,
            public = dto.public,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IncidentDetailsSchema(
    val incident: IncidentSchema,
    val notes: List<IncidentNoteSchema>,
    val integrations: List<String>,
    val resolutionIntegrations: List<String>?,
) {
    companion object {
        fun fromDto(dto: IncidentDetailsDto) = IncidentDetailsSchema(
            incident = IncidentSchema.fromDto(dto.incident),
            notes = dto.notes.map { IncidentNoteSchema.fromDto(it) },
            integrations = dto.integrations.map { it.toString() },
            resolutionIntegrations = dto.resolutionIntegrations?.map { it.toString() },
        )
    }
}

@JsonSchema
@Introspected
data class IncidentNoteCreatorSchema(
    val incidentType: IncidentType,
    val eventId: Long,
    val status: InvestigationStatus,
    @get:NotBlank
    val content: String,
    val timestamp: OffsetDateTime? = null,
    val public: Boolean? = null,
) {
    fun toDto() = IncidentNoteCreateDto(
        status = status,
        content = content,
        timestamp = timestamp,
        public = public ?: false,
    )
}
