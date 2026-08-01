package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.tcpMonitorDetailsContent(monitor: TcpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.TCP,
        monitor = monitor,
        uptimeSummary = { detailsTcpUptimeSummary(monitor, stats) },
    ) {
        // Latency metrics
        if (monitor.metricsHistoryEnabled) {
            tcpDetailsMetricsBlock(monitor)
        }
    }
