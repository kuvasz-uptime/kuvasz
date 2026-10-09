package com.kuvaszuptime.kuvasz.models.dto.incident

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class IncidentDetailsDto(
    @param:Schema(description = IncidentDocs.INCIDENT, required = true)
    val incident: IncidentDto,
    @param:Schema(description = IncidentDocs.NOTES, required = true)
    val notes: List<IncidentNoteDto>,
    @param:Schema(description = IncidentDocs.INTEGRATIONS, required = true)
    val integrations: List<IntegrationID>,
    @param:Schema(description = IncidentDocs.RESOLUTION_INTEGRATIONS, required = true, nullable = true)
    val resolutionIntegrations: List<IntegrationID>?,
)
