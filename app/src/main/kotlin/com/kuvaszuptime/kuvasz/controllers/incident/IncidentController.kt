package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDocs.INCIDENT_NOTES_405_REASON
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.services.incident.IncidentActions
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

@Controller("${API_V2_PREFIX}/incidents", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.INCIDENTS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class IncidentController(
    private val incidentRepository: IncidentRepository,
    private val incidentActions: IncidentActions,
) : IncidentOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = IncidentDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getIncidents(
        @QueryValue monitorId: Long?,
        @QueryValue period: Duration?,
        @QueryValue includeResolved: Boolean?
    ): List<IncidentDto> =
        incidentRepository.getIncidents(
            monitorId = monitorId,
            period = period ?: Duration.ofDays(IncidentQueryDefaults.PERIOD_DAYS),
            includeResolved = includeResolved ?: IncidentQueryDefaults.INCLUDE_RESOLVED,
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = IncidentDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getIncidentDetails(incidentType: IncidentType, eventId: Long): IncidentDetailsDto =
        incidentActions.getIncidentDetails(incidentType, eventId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = IncidentNoteDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = INCIDENT_NOTES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIncidentNotesWritable
    override fun createIncidentNote(
        incidentType: IncidentType,
        eventId: Long,
        @Valid note: IncidentNoteCreateDto,
    ): IncidentNoteDto = incidentActions.createNote(incidentType, eventId, note)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = IncidentNoteDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = INCIDENT_NOTES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIncidentNotesWritable
    override fun updateIncidentNote(incidentNoteId: Long, updates: ObjectNode): IncidentNoteDto =
        incidentActions.updateNote(incidentNoteId, updates)

    @Status(HttpStatus.NO_CONTENT)
    @ApiResponses(
        ApiResponse(
            responseCode = "204",
            description = "Successful deletion"
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = INCIDENT_NOTES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIncidentNotesWritable
    override fun deleteIncidentNote(incidentNoteId: Long) = incidentActions.deleteNote(incidentNoteId)
}
