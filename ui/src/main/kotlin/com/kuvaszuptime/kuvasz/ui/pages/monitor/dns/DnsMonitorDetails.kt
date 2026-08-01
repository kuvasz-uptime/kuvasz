package com.kuvaszuptime.kuvasz.ui.pages.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.*

fun renderDnsMonitorDetailsPage(
    globals: AppGlobals,
    monitor: DnsMonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
): String =
    renderMonitorDetailsPage(
        globals = globals,
        monitor = monitor,
        typeUiConfig = MonitorTypeUiConfig.DNS,
        heading = { dnsMonitorDetailsHeading(monitor) },
        upsertModal = { modalId -> dnsMonitorCreateUpdateModal(modalId, monitor, globals) },
        content = { dnsMonitorDetailsContent(monitor, stats) },
    )
