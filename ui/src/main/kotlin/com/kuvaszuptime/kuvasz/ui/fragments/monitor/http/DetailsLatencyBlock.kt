package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.detailsMetricsBlock(monitor: HttpMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
        statPeriodInHours = UIDefaults.HTTP_MONITOR_LATENCY_STATS_PERIOD_HOURS,
    ) {
        latencyMetricCards()
        // The block has a single section, so its heading comes from the details content and the auto-refresh toggle
        // sits in the chart's header instead of next to a heading of its own
        metricsChartCard(chartElementId = "monitor-details-latency-chart", withAutoRefreshToggle = true)
    }
