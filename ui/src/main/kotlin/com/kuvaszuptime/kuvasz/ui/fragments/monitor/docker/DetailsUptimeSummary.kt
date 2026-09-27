package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDockerUptimeSummary(monitor: DockerMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsDockerUptimeSummary(monitor, stats) } }

fun FlowContent.detailsDockerUptimeSummary(monitor: DockerMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.DOCKER,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.DOCKER_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        pendingLabel = Messages.waitingForCheck(),
        lastCheckLabel = Messages.lastCheck(),
        lastCheckAt = monitor.lastUptimeCheck,
        nextCheckLabel = Messages.nextCheck(),
        nextCheckAt = monitor.nextUptimeCheck,
    )
