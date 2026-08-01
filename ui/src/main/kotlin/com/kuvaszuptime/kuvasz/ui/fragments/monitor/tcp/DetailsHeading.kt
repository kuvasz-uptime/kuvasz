package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderTcpMonitorDetailsHeading(monitor: TcpMonitorDetailsDto): String =
    buildString { appendHTML().div { tcpMonitorDetailsHeading(monitor) } }

internal fun FlowContent.tcpMonitorDetailsHeading(monitor: TcpMonitorDetailsDto) =
    monitorDetailsHeading(MonitorTypeUiConfig.TCP, monitor) {
        monitorTargetBadge(
            text = "${monitor.host.abbreviate(MONITOR_TARGET_MAX_LENGTH)}:${monitor.port}",
            icon = Icon.VIEWFINDER,
        )
    }
