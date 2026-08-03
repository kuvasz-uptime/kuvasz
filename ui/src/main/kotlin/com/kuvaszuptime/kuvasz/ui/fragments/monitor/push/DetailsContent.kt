package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*

internal fun FlowContent.pushMonitorDetailsContent(monitor: PushMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        monitor = monitor,
        uptimeSummary = { detailsPushUptimeSummary(monitor, stats) },
    )
