package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderTcpMonitoringStats(
    monitoringStats: TcpMonitoringStatsDto,
    downMonitors: List<TcpMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        uptimeStatsSection(
            typeUiConfig = MonitorTypeUiConfig.TCP,
            actualStats = monitoringStats.actual.uptimeStats,
            historyStats = monitoringStats.history.uptimeStats,
            downMonitors = downMonitors,
            columns = listOf(lastCheckColumn()),
        )
    }
