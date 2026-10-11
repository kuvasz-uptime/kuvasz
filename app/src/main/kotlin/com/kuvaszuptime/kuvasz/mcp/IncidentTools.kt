package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.incident.CheckIncidentNotesWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_INCIDENT_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INCIDENTS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentNoteCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentSchema
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.services.incident.IncidentActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import java.time.Duration

private const val INCIDENTS_PERIOD_DAYS = 7L
private val DEFAULT_INCIDENTS_PERIOD = Duration.ofDays(INCIDENTS_PERIOD_DAYS)
// Has to be a constant to be used in the annotations, every IncidentType has to be listed here
private const val INCIDENT_TYPES = "'HTTP', 'SSL', 'PUSH', 'ICMP', 'TCP', 'DNS', 'DOCKER'"

@Singleton
class IncidentTools(
    private val incidentRepository: IncidentRepository,
    private val incidentActions: IncidentActions,
    private val validator: Validator,
) {

    @Tool(
        name = LIST_INCIDENTS,
        description = "Lists incidents across all monitor types (HTTP, SSL, Push, ICMP, TCP, DNS, Docker). " +
            "Defaults to the last 7 days including resolved incidents. " +
            "Optionally filter by monitor ID. An incident is identified by its 'incidentType' and 'eventId' " +
            "together, use them with the $GET_INCIDENT_DETAILS tool to get its notes and notified integrations.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun listIncidents(
        @ToolArg(description = "Filter by a specific monitor ID. Omit to return incidents for all monitors.")
        monitorId: Long? = null,
        @ToolArg(description = "ISO 8601 duration look-back window, e.g. 'P7D' or 'PT12H'. Defaults to P7D.")
        period: String? = null,
        @ToolArg(description = "Whether to include resolved incidents alongside ongoing ones. Defaults to true.")
        includeResolved: Boolean? = null,
    ): IncidentListSchema =
        IncidentListSchema(
            incidents = incidentRepository.getIncidents(
                monitorId = monitorId,
                period = period.asDuration() ?: DEFAULT_INCIDENTS_PERIOD,
                includeResolved = includeResolved ?: true,
            ).map { IncidentSchema.fromDto(it) }
        )

    @Tool(
        name = GET_INCIDENT_DETAILS,
        description = "Get the details of an incident: the incident itself, its notes (ordered by their " +
            "timestamp), the integrations that were notified when it started, and the ones notified when it was " +
            "resolved (null while it's ongoing).",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getIncidentDetails(
        @ToolArg(description = "The type of the incident, one of: $INCIDENT_TYPES") incidentType: String,
        @ToolArg(description = "The 'eventId' of the incident, as returned by $LIST_INCIDENTS") eventId: Long,
    ): IncidentDetailsSchema =
        IncidentDetailsSchema.fromDto(incidentActions.getIncidentDetails(incidentType.toIncidentType(), eventId))

    @Tool(
        name = ToolNames.CREATE_INCIDENT_NOTE,
        description = "Adds a note to an incident, identified by its 'incidentType' and 'eventId'. " +
            "'status' is the state of the investigation the note describes. The optional 'timestamp' overrides the " +
            "creation date of the note where it's displayed, and orders the notes of the incident. A 'public' note " +
            "is shown on the status pages that contain the monitor of the incident (SSL incidents are never shown " +
            "there). Returns the details of the incident, including the new note. To change a note, delete and " +
            "recreate it, the timestamp of the original note can be kept by passing it explicitly. " +
            "It returns an error if incident notes are disabled on the instance.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckIncidentNotesWritable
    fun createIncidentNote(input: IncidentNoteCreatorSchema): IncidentDetailsSchema {
        val createDto = input.toDto()
        validator.validate(createDto).throwIfNotEmpty()
        return IncidentDetailsSchema.fromDto(
            incidentActions.createNoteAndGetDetails(input.incidentType, input.eventId, createDto)
        )
    }

    @Tool(
        name = ToolNames.DELETE_INCIDENT_NOTE,
        description = "Permanently deletes an incident note by its ID. " +
            "It returns an error if incident notes are disabled on the instance.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckIncidentNotesWritable
    fun deleteIncidentNote(
        @ToolArg(description = "The numeric ID of the incident note") incidentNoteId: Long,
    ): DeleteResultSchema {
        incidentActions.deleteNote(incidentNoteId)
        return DeleteResultSchema(deleted = true, id = incidentNoteId)
    }

    private fun String.toIncidentType(): IncidentType =
        IncidentType.fromNameOrNull(this)
            ?: throw ValidationException("Unknown incident type: '$this', it should be one of: $INCIDENT_TYPES")
}
