package com.kuvaszuptime.kuvasz.ui.pages.monitor.docker

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDockerMonitorDetailsPage(
    globals: AppGlobals,
    monitor: DockerMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        heading = { dockerMonitorDetailsHeading(monitor, globals.configuredDockerHosts) },
        upsertModal = { modalId -> dockerMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { dockerMonitorDetailsContent(monitor, stats) },
    )
