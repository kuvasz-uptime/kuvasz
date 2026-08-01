package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.dnsDetailsMetricsBlock(monitor: DnsMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.DNS,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
        statPeriodInHours = UIDefaults.DNS_MONITOR_METRICS_PERIOD_HOURS,
    ) {
        metricsSectionHeading(UIDefaults.DNS_MONITOR_METRICS_PERIOD_HOURS)

        h3 { +Messages.latencyBlockTitle() }
        latencyMetricCards()
        metricsChartCard(chartElementId = "dns-monitor-details-latency-chart")
    }
