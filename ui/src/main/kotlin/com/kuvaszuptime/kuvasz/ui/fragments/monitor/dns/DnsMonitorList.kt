package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias DnsMonitorListQuery = ListQuery<DnsMonitorSortField, MonitorListFilters>

fun dnsMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): DnsMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.DNS,
        filters = MonitorListFilters(params, availableCategories),
        sortFields = DnsMonitorSortField.entries,
        defaultSorting = ListDefaults.DNS_MONITOR_SORTING,
    )

fun renderDnsMonitorList(
    monitors: PagedResult<DnsMonitorDetailsDto>,
    query: DnsMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.DNS,
        editabilityState = editabilityState,
        nameSortField = DnsMonitorSortField.NAME,
        statusSortField = DnsMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            uptimeStatusChangedColumn(DnsMonitorSortField.UPTIME_STATUS_STARTED_AT),
            nextCheckColumn(D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
    )
