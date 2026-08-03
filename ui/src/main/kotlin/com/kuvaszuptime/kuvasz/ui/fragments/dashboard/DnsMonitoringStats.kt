package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDnsMonitoringStats(
    monitoringStats: DnsMonitoringStatsDto,
    downMonitors: List<DnsMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        uptimeStatsSection(
            typeUiConfig = MonitorTypeUiConfig.DNS,
            actualStats = monitoringStats.actual.uptimeStats,
            historyStats = monitoringStats.history.uptimeStats,
            downMonitors = downMonitors,
            columns = listOf(lastCheckColumn()),
        )
    }
