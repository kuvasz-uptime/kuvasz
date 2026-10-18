package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V3_PREFIX
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.Duration

@Controller("${API_V3_PREFIX}/incidents", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.INCIDENTS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class IncidentV3Controller(
    private val incidentRepository: IncidentRepository,
) : IncidentV3Operations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getIncidentsPage(
        monitorId: Long?,
        period: Duration?,
        includeResolved: Boolean?,
        name: String?,
        page: Int,
        size: Int,
        sortedBy: IncidentSortField?,
        sortOrder: SortOrder?,
    ): PagedResult<IncidentDto> =
        incidentRepository.getIncidentsPage(
            monitorId = monitorId,
            period = period ?: Duration.ofDays(IncidentQueryDefaults.PERIOD_DAYS),
            includeResolved = includeResolved ?: IncidentQueryDefaults.INCLUDE_RESOLVED,
            type = null,
            name = name,
            sorting = Sorting.of(sortedBy, sortOrder, default = IncidentQueryDefaults.SORTING),
            pageRequest = PageRequest(page, size),
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getIncidentsOfTypePage(
        incidentType: IncidentType,
        monitorId: Long?,
        period: Duration?,
        includeResolved: Boolean?,
        name: String?,
        page: Int,
        size: Int,
        sortedBy: IncidentSortField?,
        sortOrder: SortOrder?,
    ): PagedResult<IncidentDto> =
        incidentRepository.getIncidentsPage(
            monitorId = monitorId,
            period = period ?: Duration.ofDays(IncidentQueryDefaults.PERIOD_DAYS),
            includeResolved = includeResolved ?: IncidentQueryDefaults.INCLUDE_RESOLVED,
            type = incidentType,
            name = name,
            sorting = Sorting.of(sortedBy, sortOrder, default = IncidentQueryDefaults.SORTING),
            pageRequest = PageRequest(page, size),
        )
}
