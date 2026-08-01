package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderIcmpUptimeSummary(monitor: IcmpMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsIcmpUptimeSummary(monitor, stats) } }

fun FlowContent.detailsIcmpUptimeSummary(monitor: IcmpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        pendingLabel = Messages.waitingForCheck(),
        lastCheckLabel = Messages.lastCheck(),
        lastCheckAt = monitor.lastUptimeCheck,
        nextCheckLabel = Messages.nextCheck(),
        nextCheckAt = monitor.nextUptimeCheck,
    )
