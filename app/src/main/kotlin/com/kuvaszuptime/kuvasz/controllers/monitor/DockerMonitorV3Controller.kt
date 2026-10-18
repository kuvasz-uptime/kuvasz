package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V3_PREFIX
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.event.DockerUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorActions
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("${API_V3_PREFIX}/docker-monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.DOCKER_MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class DockerMonitorV3Controller(
    private val monitorActions: DockerMonitorActions,
) : DockerMonitorV3Operations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitorsPage(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>?,
        category: String?,
        name: String?,
        page: Int,
        size: Int,
        sortedBy: DockerMonitorSortField?,
        sortOrder: SortOrder?,
    ): PagedResult<DockerMonitorDetailsDto> =
        monitorActions.getMonitorsPage(
            enabled = enabled,
            uptimeStatus = uptimeStatus.orEmpty(),
            categoryFilter = CategoryFilter.fromQueryParam(category),
            name = name,
            sorting = Sorting.of(sortedBy, sortOrder, default = DEFAULT_SORTING),
            pageRequest = PageRequest(page, size),
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getUptimeEventsPage(
        monitorId: Long,
        page: Int,
        size: Int,
        sortedBy: EventSortField?,
        sortOrder: SortOrder?,
    ): PagedResult<DockerUptimeEventDto> =
        monitorActions.getUptimeEventsPage(
            monitorId = monitorId,
            sorting = Sorting.of(sortedBy, sortOrder, default = DEFAULT_EVENT_SORTING),
            pageRequest = PageRequest(page, size),
        )

    companion object {
        private val DEFAULT_SORTING = Sorting(DockerMonitorSortField.NAME, SortOrder.ASC)
        private val DEFAULT_EVENT_SORTING = Sorting(EventSortField.STARTED_AT, SortOrder.DESC)
    }
}
