package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

fun renderDnsMonitorDetailsHeading(monitor: DnsMonitorDetailsDto): String =
    buildString { appendHTML().div { dnsMonitorDetailsHeading(monitor) } }

private const val MONITOR_HOST_MAX_LENGTH = 40

internal fun FlowContent.dnsMonitorDetailsHeading(monitor: DnsMonitorDetailsDto) {
    div {
        id = "dns-monitor-detail-heading"
        classes(COL_AUTO)
        hx {
            get("/dns-monitors/fragments/details-heading/${monitor.id}")
            trigger {
                every(15.seconds)
                event("refresh-monitor-detail-status")
            }
            onSwapReinitTooltips()
        }

        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_AUTO)
                uptimeStatusOfMonitor(monitor, withTooltip = false)
            }
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +"#${monitor.id}"
                }
                h2 {
                    classes(PAGE_TITLE, TEXT_WRAP, TEXT_BREAK)
                    +monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH)
                }
                div {
                    classes(TEXT_SECONDARY)
                    ul {
                        classes(LIST_INLINE, MT_1, MB_0)
                        a(href = "/dns-monitors") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = "DNS",
                                icon = Icon.CLOUD_QUESTION,
                                color = Color.CYAN_LT
                            )
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = monitor.host.abbreviate(MONITOR_HOST_MAX_LENGTH),
                                icon = Icon.VIEWFINDER,
                                color = Color.DEFAULT,
                            )
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = monitor.transport.literal,
                                icon = Icon.NETWORK,
                                color = Color.DEFAULT,
                            )
                        }
                    }
                }
            }
        }
    }
}
