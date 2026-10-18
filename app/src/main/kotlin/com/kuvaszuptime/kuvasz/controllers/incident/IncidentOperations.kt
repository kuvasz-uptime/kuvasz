package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteUpdateDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

interface IncidentOperations {

    @Operation(
        summary = "Get all incidents",
        description = "Deprecated: use GET /api/v3/incidents instead, which returns the incidents page by page",
        deprecated = true,
    )
    @Get("/")
    fun getIncidents(
        @QueryValue
        @Parameter(required = false)
        monitorId: Long?,
        @QueryValue
        @Parameter(
            required = false,
            schema = Schema(
                implementation = Duration::class,
                description = "An ISO-8601 Duration string, default P7D",
            )
        )
        period: Duration?,
        @QueryValue
        @Parameter(
            required = false,
            description = "If false, only ongoing incidents are returned, default true",
        )
        includeResolved: Boolean?,
    ): List<IncidentDto>

    @Operation(
        summary = "Get the details of an incident",
        description = "Returns the incident along with its notes, and the integrations that were notified when it " +
            "started and when it was resolved.",
    )
    @Get("/{incidentType}/{eventId}")
    fun getIncidentDetails(incidentType: IncidentType, eventId: Long): IncidentDetailsDto

    @Operation(summary = "Add a note to an incident")
    @Post("/{incidentType}/{eventId}/notes")
    fun createIncidentNote(
        incidentType: IncidentType,
        eventId: Long,
        @Body note: IncidentNoteCreateDto,
    ): IncidentNoteDto

    @Operation(
        summary = "Update an incident note by ID",
        description = "Updates the incident note with the given ID. Only fields that are present in the request body " +
            "will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(
            content = [Content(schema = Schema(implementation = IncidentNoteUpdateDto::class))]
        )
    )
    @Patch("/notes/{incidentNoteId}")
    fun updateIncidentNote(incidentNoteId: Long, @Body updates: ObjectNode): IncidentNoteDto

    @Operation(summary = "Delete an incident note by ID")
    @Delete("/notes/{incidentNoteId}")
    fun deleteIncidentNote(incidentNoteId: Long)
}
