package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.icmpMonitorDetailsContent(monitor: IcmpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        monitor = monitor,
        uptimeSummary = { detailsIcmpUptimeSummary(monitor, stats) },
    ) {
        // Latency and packet loss metrics
        if (monitor.metricsHistoryEnabled) {
            icmpDetailsMetricsBlock(monitor)
        }
    }
