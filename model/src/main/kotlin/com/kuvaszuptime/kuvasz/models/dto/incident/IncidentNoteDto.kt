package com.kuvaszuptime.kuvasz.models.dto.incident

import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.IncidentNoteValidationMessages
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

@Introspected
data class IncidentNoteDto(
    @param:Schema(description = IncidentNoteDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = IncidentNoteDocs.INCIDENT_TYPE, required = true)
    val incidentType: IncidentType,
    @param:Schema(description = IncidentNoteDocs.EVENT_ID, required = true)
    val eventId: Long,
    @param:Schema(description = IncidentNoteDocs.STATUS, required = true)
    val status: InvestigationStatus,
    @param:Schema(description = IncidentNoteDocs.CONTENT, required = true)
    val content: String,
    @param:Schema(description = IncidentNoteDocs.TIMESTAMP, required = true, nullable = true)
    val timestamp: OffsetDateTime?,
    @param:Schema(description = IncidentNoteDocs.PUBLIC, required = true)
    val public: Boolean,
    @param:Schema(description = IncidentNoteDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = IncidentNoteDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
)

@Introspected
data class IncidentNoteCreateDto(
    @param:Schema(description = IncidentNoteDocs.STATUS, required = true)
    val status: InvestigationStatus,
    @get:NotBlank(message = IncidentNoteValidationMessages.CONTENT_NOT_BLANK)
    @param:Schema(description = IncidentNoteDocs.CONTENT, required = true)
    val content: String,
    @param:Schema(description = IncidentNoteDocs.TIMESTAMP, required = false, nullable = true)
    val timestamp: OffsetDateTime? = null,
    @param:Schema(description = IncidentNoteDocs.PUBLIC, required = false, defaultValue = "false")
    val public: Boolean = false,
)

/**
 * The fields a PATCH request can update, used for the validation of the merged result, and as the request body in the
 * OpenAPI spec.
 */
@Introspected
data class IncidentNoteUpdateDto(
    @get:NotNull
    @param:Schema(description = IncidentNoteDocs.STATUS, required = false, nullable = false)
    val status: InvestigationStatus?,
    @get:NotBlank(message = IncidentNoteValidationMessages.CONTENT_NOT_BLANK)
    @param:Schema(description = IncidentNoteDocs.CONTENT, required = false, nullable = false)
    val content: String?,
    @param:Schema(description = IncidentNoteDocs.TIMESTAMP, required = false, nullable = true)
    val timestamp: OffsetDateTime?,
    @get:NotNull
    @param:Schema(description = IncidentNoteDocs.PUBLIC, required = false, nullable = false)
    val public: Boolean?,
)
