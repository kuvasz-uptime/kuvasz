package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderPushMonitorDetailsHeading(monitor: PushMonitorDetailsDto): String =
    buildString { appendHTML().div { pushMonitorDetailsHeading(monitor) } }

// A push monitor has no target of its own: its clients come to Kuvasz, so there is nothing to badge beyond the type
internal fun FlowContent.pushMonitorDetailsHeading(monitor: PushMonitorDetailsDto) =
    monitorDetailsHeading(MonitorTypeUiConfig.PUSH, monitor)
