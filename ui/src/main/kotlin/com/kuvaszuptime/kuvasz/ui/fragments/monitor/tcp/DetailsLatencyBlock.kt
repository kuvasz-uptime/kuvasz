package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.tcpDetailsMetricsBlock(monitor: TcpMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.TCP,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
        statPeriodInHours = UIDefaults.TCP_MONITOR_METRICS_PERIOD_HOURS,
    ) {
        metricsSectionHeading(UIDefaults.TCP_MONITOR_METRICS_PERIOD_HOURS)

        h3 { +Messages.latencyBlockTitle() }
        latencyMetricCards()
        metricsChartCard(chartElementId = "tcp-monitor-details-latency-chart")
    }
