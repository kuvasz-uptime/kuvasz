package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderPushMonitorDetailsPage(
    globals: AppGlobals,
    monitor: PushMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        heading = { pushMonitorDetailsHeading(monitor) },
        upsertModal = { modalId -> pushMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { pushMonitorDetailsContent(monitor, stats) },
    )
