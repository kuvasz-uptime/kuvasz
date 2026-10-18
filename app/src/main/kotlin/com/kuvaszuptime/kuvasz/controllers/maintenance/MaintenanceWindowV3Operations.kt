package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.PaginationDocs
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Min

interface MaintenanceWindowV3Operations {

    @Operation(summary = "Get the maintenance windows, page by page")
    @Get("/")
    fun getMaintenanceWindowsPage(
        @QueryValue
        @Parameter(required = false)
        enabled: Boolean?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.MAINTENANCE_WINDOW_NAME_FILTER)
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
        @Parameter(required = false, description = PaginationDocs.MAINTENANCE_WINDOW_SORTED_BY)
        sortedBy: MaintenanceWindowSortField?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.SORT_ORDER)
        sortOrder: SortOrder?,
    ): PagedResult<MaintenanceWindowDetailsDto>
}
