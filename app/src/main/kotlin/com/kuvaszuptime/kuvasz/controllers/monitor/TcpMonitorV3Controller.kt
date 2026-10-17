package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V3_PREFIX
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorActions
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("${API_V3_PREFIX}/tcp-monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.TCP_MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class TcpMonitorV3Controller(
    private val monitorActions: TcpMonitorActions,
) : TcpMonitorV3Operations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitorsPage(
        @QueryValue enabled: Boolean?,
        @QueryValue uptimeStatus: List<UptimeStatus>?,
        @QueryValue category: String?,
        @QueryValue name: String?,
        @QueryValue(defaultValue = PageRequest.DEFAULT_PAGE_PARAM) page: Int,
        @QueryValue(defaultValue = PageRequest.DEFAULT_SIZE_PARAM) size: Int,
        @QueryValue sortedBy: TcpMonitorSortField?,
        @QueryValue sortOrder: SortOrder?,
    ): PagedResult<TcpMonitorDetailsDto> =
        monitorActions.getMonitorsPage(
            enabled = enabled,
            uptimeStatus = uptimeStatus.orEmpty(),
            categoryFilter = CategoryFilter.fromQueryParam(category),
            name = name,
            sorting = Sorting.of(sortedBy, sortOrder, default = DEFAULT_SORTING),
            pageRequest = PageRequest(page, size),
        )

    companion object {
        private val DEFAULT_SORTING = Sorting(TcpMonitorSortField.NAME, SortOrder.ASC)
    }
}
