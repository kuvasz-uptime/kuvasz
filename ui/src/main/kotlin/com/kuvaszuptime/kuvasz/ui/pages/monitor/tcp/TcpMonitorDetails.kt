package com.kuvaszuptime.kuvasz.ui.pages.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderTcpMonitorDetailsPage(
    globals: AppGlobals,
    monitor: TcpMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        heading = { tcpMonitorDetailsHeading(monitor) },
        upsertModal = { modalId -> tcpMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { tcpMonitorDetailsContent(monitor, stats) },
    )
