package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.icmpDetailsMetricsBlock(monitor: IcmpMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
        statPeriodInHours = UIDefaults.ICMP_MONITOR_METRICS_PERIOD_HOURS,
    ) {
        metricsSectionHeading(UIDefaults.ICMP_MONITOR_METRICS_PERIOD_HOURS)

        h3 { +Messages.latencyBlockTitle() }
        latencyMetricCards()
        metricsChartCard(chartElementId = "icmp-monitor-details-latency-chart")

        h3 { +Messages.packetLossBlockTitle() }
        packetLossMetricCards()
        metricsChartCard(chartElementId = "icmp-monitor-details-packet-loss-chart")
    }
