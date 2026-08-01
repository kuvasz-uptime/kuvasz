package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderIcmpMonitorDetailsHeading(monitor: IcmpMonitorDetailsDto): String =
    buildString { appendHTML().div { icmpMonitorDetailsHeading(monitor) } }

internal fun FlowContent.icmpMonitorDetailsHeading(monitor: IcmpMonitorDetailsDto) =
    monitorDetailsHeading(MonitorTypeUiConfig.ICMP, monitor) {
        monitorTargetBadge(monitor.host.abbreviate(MONITOR_TARGET_MAX_LENGTH), Icon.VIEWFINDER)
    }
