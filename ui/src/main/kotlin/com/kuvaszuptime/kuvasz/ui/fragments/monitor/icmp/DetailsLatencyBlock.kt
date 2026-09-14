package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.icmpDetailsMetricsBlock(monitor: IcmpMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
    ) {
        h3 { +Messages.latencyBlockTitle() }
        latencyMetricCards()

        h3 { +Messages.packetLossBlockTitle() }
        packetLossMetricCards()

        metricsChartCard(chartElementId = "icmp-monitor-details-metrics-chart")
    }
