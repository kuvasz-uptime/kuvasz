package com.kuvaszuptime.kuvasz.ui.pages.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderHttpMonitorDetailsPage(
    globals: AppGlobals,
    monitor: HttpMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        heading = { httpMonitorDetailsHeading(monitor) },
        upsertModal = { modalId -> httpMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { httpMonitorDetailsContent(monitor, stats) },
    )
