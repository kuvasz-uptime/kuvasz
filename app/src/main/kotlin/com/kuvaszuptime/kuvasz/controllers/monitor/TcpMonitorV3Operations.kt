package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.PaginationDocs
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.Min

interface TcpMonitorV3Operations {

    @Operation(summary = "Get the monitors with their details, page by page")
    @Get("/")
    fun getMonitorsPage(
        @QueryValue
        @Parameter(required = false)
        enabled: Boolean?,
        @QueryValue
        @Parameter(required = false)
        uptimeStatus: List<UptimeStatus>?,
        @QueryValue
        @Parameter(required = false, description = MonitorDocs.CATEGORY_FILTER)
        category: String?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.MONITOR_NAME_FILTER)
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
        @Parameter(required = false, description = PaginationDocs.MONITOR_SORTED_BY)
        sortedBy: TcpMonitorSortField?,
        @QueryValue
        @Parameter(required = false, description = PaginationDocs.SORT_ORDER)
        sortOrder: SortOrder?,
    ): PagedResult<TcpMonitorDetailsDto>
}
