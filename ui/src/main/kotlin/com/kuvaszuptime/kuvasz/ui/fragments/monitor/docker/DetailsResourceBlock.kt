package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

/**
 * The container's own resource use. There is no latency block here on purpose: the only timing a Docker check
 * produces is the round-trip to the daemon, which says nothing about the container.
 */
internal fun FlowContent.dockerDetailsMetricsBlock(monitor: DockerMonitorDetailsDto) =
    monitorMetricsBlock(
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        monitorId = monitor.id,
        isMonitorEnabled = monitor.enabled,
        uptimeCheckInterval = monitor.uptimeCheckInterval,
    ) {
        h3 { +Messages.dockerResourceBlockTitle() }
        dockerResourceMetricCards()

        metricsChartCard(chartElementId = "docker-monitor-details-metrics-chart")
    }
