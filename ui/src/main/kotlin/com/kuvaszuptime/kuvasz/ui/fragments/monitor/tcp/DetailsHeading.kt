package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderTcpMonitorDetailsHeading(monitor: TcpMonitorDetailsDto, configuredProxies: List<String>): String =
    buildString { appendHTML().div { tcpMonitorDetailsHeading(monitor, configuredProxies) } }

internal fun FlowContent.tcpMonitorDetailsHeading(monitor: TcpMonitorDetailsDto, configuredProxies: List<String>) =
    monitorDetailsHeading(MonitorTypeUiConfig.TCP, monitor) {
        monitorTargetBadge(
            text = "${monitor.host.abbreviate(MONITOR_TARGET_MAX_LENGTH)}:${monitor.port}",
            icon = Icon.VIEWFINDER,
        )
        monitor.proxy?.let { proxy -> listItemProxyBadge(proxy, configuredProxies) }
    }
