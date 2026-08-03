package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDnsMonitorDetailsHeading(monitor: DnsMonitorDetailsDto): String =
    buildString { appendHTML().div { dnsMonitorDetailsHeading(monitor) } }

internal fun FlowContent.dnsMonitorDetailsHeading(monitor: DnsMonitorDetailsDto) =
    monitorDetailsHeading(MonitorTypeUiConfig.DNS, monitor) {
        monitorTargetBadge(monitor.host.abbreviate(MONITOR_TARGET_MAX_LENGTH), Icon.VIEWFINDER)
        monitorTargetBadge(monitor.transport.literal, Icon.NETWORK)
    }
