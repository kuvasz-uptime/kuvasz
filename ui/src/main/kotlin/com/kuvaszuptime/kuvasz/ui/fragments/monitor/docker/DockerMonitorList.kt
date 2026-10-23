package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

typealias DockerMonitorListQuery = ListQuery<DockerMonitorSortField, MonitorListFilters>

fun dockerMonitorListQuery(params: QueryParams, availableCategories: List<String> = emptyList()): DockerMonitorListQuery =
    monitorListQuery(
        params = params,
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        filters = MonitorListFilters(params, availableCategories),
        sortFields = DockerMonitorSortField.entries,
        defaultSorting = ListDefaults.DOCKER_MONITOR_SORTING,
    )

fun renderDockerMonitorList(
    monitors: PagedResult<DockerMonitorDetailsDto>,
    query: DockerMonitorListQuery,
    editabilityState: AppGlobals.EditabilityState,
    configuredDockerHosts: List<String>,
): String =
    renderMonitorList(
        monitors = monitors,
        query = query,
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        editabilityState = editabilityState,
        nameSortField = DockerMonitorSortField.NAME,
        statusSortField = DockerMonitorSortField.UPTIME_STATUS,
        columns = listOf(
            uptimeStatusChangedColumn(DockerMonitorSortField.UPTIME_STATUS_STARTED_AT),
            nextCheckColumn(D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
        nameBadge = { monitor ->
            if (monitor.dockerHost !in configuredDockerHosts) {
                danglingDockerHostIconBadge(monitor.dockerHost)
            }
        },
    )
