package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderHttpUptimeSummary(monitor: HttpMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsHttpUptimeSummary(monitor, stats) } }

fun FlowContent.detailsHttpUptimeSummary(monitor: HttpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.HTTP_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        pendingLabel = Messages.waitingForCheck(),
        lastCheckLabel = Messages.lastCheck(),
        lastCheckAt = monitor.lastUptimeCheck,
        nextCheckLabel = Messages.nextCheck(),
        nextCheckAt = monitor.nextUptimeCheck,
    )
