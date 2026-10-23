package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias PushMonitorListQuery = ListQuery<PushMonitorSortField, MonitorListFilters>

fun pushMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): PushMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        filters = MonitorListFilters(params, availableCategories),
        sortFields = PushMonitorSortField.entries,
        defaultSorting = ListDefaults.PUSH_MONITOR_SORTING,
    )

fun renderPushMonitorList(
    monitors: PagedResult<PushMonitorDetailsDto>,
    query: PushMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        editabilityState = editabilityState,
        nameSortField = PushMonitorSortField.NAME,
        statusSortField = PushMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            uptimeStatusChangedColumn(PushMonitorSortField.UPTIME_STATUS_STARTED_AT),
            // A push monitor is driven by the heartbeats of its client, not by checks Kuvasz makes on its own
            timestampColumn(
                Messages.lastHeartbeat(),
                D_SM_TABLE_CELL,
                PushMonitorSortField.LAST_HEARTBEAT,
            ) { it.lastHeartbeat },
        ),
    )
