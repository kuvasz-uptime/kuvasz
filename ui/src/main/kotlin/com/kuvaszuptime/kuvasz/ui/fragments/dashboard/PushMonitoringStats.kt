package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderPushMonitoringStats(
    monitoringStats: PushMonitoringStatsDto,
    downMonitors: List<PushMonitorDetailsDto>,
): String = renderStatsSectionOfType(monitoringStats.actual.uptimeStats) {
    uptimeStatsSection(
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        actualStats = monitoringStats.actual.uptimeStats,
        historyStats = monitoringStats.history.uptimeStats,
        downMonitors = downMonitors,
        columns = listOf(
            lastCheckColumn(),
            timestampColumn(Messages.lastHeartbeat(), D_MD_TABLE_CELL) { it.lastHeartbeat },
        ),
    )
}
