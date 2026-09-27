package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.dockerMonitorDetailsContent(
    monitor: DockerMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        monitor = monitor,
        uptimeSummary = { detailsDockerUptimeSummary(monitor, stats) },
    ) {
        // CPU and memory metrics
        if (monitor.metricsHistoryEnabled) {
            dockerDetailsMetricsBlock(monitor)
        }
    }
