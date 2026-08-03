package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.dnsMonitorDetailsContent(monitor: DnsMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.DNS,
        monitor = monitor,
        uptimeSummary = { detailsDnsUptimeSummary(monitor, stats) },
    ) {
        // Latency metrics
        if (monitor.metricsHistoryEnabled) {
            dnsDetailsMetricsBlock(monitor)
        }
        // Resolved records snapshot (auto-refreshed; empty until drift detection records one). Without drift detection
        // there is never a snapshot, so the block is left out.
        if (monitor.driftDetectionEnabled) {
            autoRefreshedBlock(
                elementId = "dns-monitor-details-snapshot",
                path = MonitorTypeUiConfig.DNS.fragmentPath("snapshot/${monitor.id}"),
            )
        }
    }
