package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderIcmpMonitoringStats(
    monitoringStats: IcmpMonitoringStatsDto,
    downMonitors: List<IcmpMonitorDetailsDto>,
): String = renderStatsSectionOfType(monitoringStats.actual.uptimeStats) {
    uptimeStatsSection(
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        actualStats = monitoringStats.actual.uptimeStats,
        historyStats = monitoringStats.history.uptimeStats,
        downMonitors = downMonitors,
        columns = listOf(lastCheckColumn()),
    )
}
