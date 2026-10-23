package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias HttpMonitorListQuery = ListQuery<HttpMonitorSortField, HttpMonitorListFilters>

fun httpMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): HttpMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        filters = HttpMonitorListFilters(params, availableCategories),
        sortFields = HttpMonitorSortField.entries,
        defaultSorting = ListDefaults.HTTP_MONITOR_SORTING,
    )

fun renderHttpMonitorList(
    monitors: PagedResult<HttpMonitorDetailsDto>,
    query: HttpMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
    configuredProxies: List<String>,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        editabilityState = editabilityState,
        nameSortField = HttpMonitorSortField.NAME,
        statusSortField = HttpMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            MonitorListColumn(
                header = "SSL",
                headerClasses = setOf(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER),
                cellClasses = setOf(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER),
                sortField = HttpMonitorSortField.SSL_STATUS,
                cell = { monitor -> sslStatusOfMonitor(monitor, withTooltip = true) },
            ),
            uptimeStatusChangedColumn(HttpMonitorSortField.UPTIME_STATUS_STARTED_AT),
            nextCheckColumn(D_LG_TABLE_CELL) { it.nextUptimeCheck },
        ),
        nameTooltip = { it.url.toString() },
        nameBadge = { monitor -> monitor.proxy?.let { proxyIconBadge(it, configuredProxies) } },
    )
