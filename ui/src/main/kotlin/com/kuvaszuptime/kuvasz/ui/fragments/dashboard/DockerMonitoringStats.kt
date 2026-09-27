package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderDockerMonitoringStats(
    monitoringStats: DockerMonitoringStatsDto,
    downMonitors: List<DockerMonitorDetailsDto>,
): String = renderStatsSectionOfType(monitoringStats.actual.uptimeStats) {
    uptimeStatsSection(
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        actualStats = monitoringStats.actual.uptimeStats,
        historyStats = monitoringStats.history.uptimeStats,
        downMonitors = downMonitors,
        columns = listOf(lastCheckColumn()),
    )
}
