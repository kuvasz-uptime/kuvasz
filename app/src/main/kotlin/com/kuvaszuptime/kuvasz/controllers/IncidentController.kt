package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.IncidentDto
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
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
import java.time.Duration

@Controller("$API_V2_PREFIX/incidents", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "Incidents")
@SecurityRequirements(
    SecurityRequirement(name = "apiKey"),
    SecurityRequirement(name = "bearerAuth")
)
class IncidentController(
    private val incidentRepository: IncidentRepository,
) : IncidentOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = IncidentDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getIncidents(
        @QueryValue monitorId: Long?,
        @QueryValue period: Duration?,
        @QueryValue includeResolved: Boolean?
    ): List<IncidentDto> =
        incidentRepository.getIncidents(
            monitorId = monitorId,
            period = period ?: Duration.ofDays(INCIDENTS_PERIOD_DEFAULT_DAYS),
            includeResolved = includeResolved ?: INCLUDE_RESOLVED_DEFAULT,
        )

    companion object {
        private const val INCIDENTS_PERIOD_DEFAULT_DAYS = 7L
        private const val INCLUDE_RESOLVED_DEFAULT = true
    }
}
