package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.PaginationDocs
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import java.time.Duration

interface IncidentV3Operations {

    @Operation(summary = "Get the incidents of every type, page by page")
    @Get("/")
    fun getIncidentsPage(
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
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.INCIDENT_NAME_FILTER)
        name: String?,
        @QueryValue(defaultValue = PageRequest.DEFAULT_PAGE_PARAM)
        @Parameter(required = false, description = PaginationDocs.PAGE)
        @Min(1)
        page: Int,
        @QueryValue(defaultValue = PageRequest.DEFAULT_SIZE_PARAM)
        @Parameter(required = false, description = PaginationDocs.SIZE)
        @Min(1)
        size: Int,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.INCIDENT_SORTED_BY)
        sortedBy: IncidentSortField?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.SORT_ORDER)
        sortOrder: SortOrder?,
    ): PagedResult<IncidentDto>

    @Operation(summary = "Get the incidents of one type, page by page")
    @Get("/{incidentType}")
    fun getIncidentsOfTypePage(
        incidentType: IncidentType,
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
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.INCIDENT_NAME_FILTER)
        name: String?,
        @QueryValue(defaultValue = PageRequest.DEFAULT_PAGE_PARAM)
        @Parameter(required = false, description = PaginationDocs.PAGE)
        @Min(1)
        page: Int,
        @QueryValue(defaultValue = PageRequest.DEFAULT_SIZE_PARAM)
        @Parameter(required = false, description = PaginationDocs.SIZE)
        @Min(1)
        size: Int,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.INCIDENT_SORTED_BY)
        sortedBy: IncidentSortField?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.SORT_ORDER)
        sortOrder: SortOrder?,
    ): PagedResult<IncidentDto>
}
