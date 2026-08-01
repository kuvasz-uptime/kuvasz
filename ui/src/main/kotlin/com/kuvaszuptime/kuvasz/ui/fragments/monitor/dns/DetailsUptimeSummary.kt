package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDnsUptimeSummary(monitor: DnsMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsDnsUptimeSummary(monitor, stats) } }

fun FlowContent.detailsDnsUptimeSummary(monitor: DnsMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.DNS,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        pendingLabel = Messages.waitingForCheck(),
        lastCheckLabel = Messages.lastCheck(),
        lastCheckAt = monitor.lastUptimeCheck,
        nextCheckLabel = Messages.nextCheck(),
        nextCheckAt = monitor.nextUptimeCheck,
    )
