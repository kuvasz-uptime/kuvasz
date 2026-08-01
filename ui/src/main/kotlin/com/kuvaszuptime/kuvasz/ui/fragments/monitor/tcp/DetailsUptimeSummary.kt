package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderTcpUptimeSummary(monitor: TcpMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsTcpUptimeSummary(monitor, stats) } }

fun FlowContent.detailsTcpUptimeSummary(monitor: TcpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.TCP,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        pendingLabel = Messages.waitingForCheck(),
        lastCheckLabel = Messages.lastCheck(),
        lastCheckAt = monitor.lastUptimeCheck,
        nextCheckLabel = Messages.nextCheck(),
        nextCheckAt = monitor.nextUptimeCheck,
    )
