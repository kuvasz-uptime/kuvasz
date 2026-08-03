package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderIcmpMonitoringStats(
    monitoringStats: IcmpMonitoringStatsDto,
    downMonitors: List<IcmpMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        uptimeStatsSection(
            typeUiConfig = MonitorTypeUiConfig.ICMP,
            actualStats = monitoringStats.actual.uptimeStats,
            historyStats = monitoringStats.history.uptimeStats,
            downMonitors = downMonitors,
            columns = listOf(lastCheckColumn()),
        )
    }
