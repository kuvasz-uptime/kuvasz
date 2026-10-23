package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias TcpMonitorListQuery = ListQuery<TcpMonitorSortField, MonitorListFilters>

fun tcpMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): TcpMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        filters = MonitorListFilters(params, availableCategories),
        sortFields = TcpMonitorSortField.entries,
        defaultSorting = ListDefaults.TCP_MONITOR_SORTING,
    )

fun renderTcpMonitorList(
    monitors: PagedResult<TcpMonitorDetailsDto>,
    query: TcpMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
    configuredProxies: List<String>,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        editabilityState = editabilityState,
        nameSortField = TcpMonitorSortField.NAME,
        statusSortField = TcpMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            uptimeStatusChangedColumn(TcpMonitorSortField.UPTIME_STATUS_STARTED_AT),
            nextCheckColumn(D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
        nameBadge = { monitor -> monitor.proxy?.let { proxyIconBadge(it, configuredProxies) } },
    )
