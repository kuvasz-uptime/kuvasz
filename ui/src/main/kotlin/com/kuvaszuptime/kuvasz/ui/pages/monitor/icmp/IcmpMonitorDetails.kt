package com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderIcmpMonitorDetailsPage(
    globals: AppGlobals,
    monitor: IcmpMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        heading = { icmpMonitorDetailsHeading(monitor) },
        upsertModal = { modalId -> icmpMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { icmpMonitorDetailsContent(monitor, stats) },
    )
