package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias IcmpMonitorListQuery = ListQuery<IcmpMonitorSortField, MonitorListFilters>

fun icmpMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): IcmpMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        filters = MonitorListFilters(params, availableCategories),
        sortFields = IcmpMonitorSortField.entries,
        defaultSorting = ListDefaults.ICMP_MONITOR_SORTING,
    )

fun renderIcmpMonitorList(
    monitors: PagedResult<IcmpMonitorDetailsDto>,
    query: IcmpMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        editabilityState = editabilityState,
        nameSortField = IcmpMonitorSortField.NAME,
        statusSortField = IcmpMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            uptimeStatusChangedColumn(IcmpMonitorSortField.UPTIME_STATUS_STARTED_AT),
            nextCheckColumn(D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
    )
