package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderHttpMonitorDetailsHeading(monitor: HttpMonitorDetailsDto, configuredProxies: List<String>): String =
    buildString { appendHTML().div { httpMonitorDetailsHeading(monitor, configuredProxies) } }

internal fun FlowContent.httpMonitorDetailsHeading(monitor: HttpMonitorDetailsDto, configuredProxies: List<String>) =
    monitorDetailsHeading(MonitorTypeUiConfig.HTTP, monitor) {
        a(href = "#http-monitor-details-ssl-summary") {
            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE, TEXT_WRAP, TEXT_BREAK)
            sslStatusOfMonitor(monitor, withTooltip = false)
        }
        li {
            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
            monitor.url.toString().let { monitorUrl ->
                a(href = monitorUrl) {
                    targetBlank()
                    classes(LINK_SECONDARY)
                    inlineStatusBadge(
                        text = monitorUrl.abbreviate(MONITOR_TARGET_MAX_LENGTH),
                        icon = Icon.EXTERNAL_LINK,
                        color = Color.DEFAULT,
                    )
                }
            }
        }
        monitor.proxy?.let { proxy -> listItemProxyBadge(proxy, configuredProxies) }
    }
