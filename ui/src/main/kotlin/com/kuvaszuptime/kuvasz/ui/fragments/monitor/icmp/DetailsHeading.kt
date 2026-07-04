package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

fun renderIcmpMonitorDetailsHeading(monitor: IcmpMonitorDetailsDto): String =
    buildString { appendHTML().div { icmpMonitorDetailsHeading(monitor) } }

private const val MONITOR_HOST_MAX_LENGTH = 40

internal fun FlowContent.icmpMonitorDetailsHeading(monitor: IcmpMonitorDetailsDto) {
    div {
        id = "icmp-monitor-detail-heading"
        classes(COL_AUTO)
        hx {
            get("/icmp-monitors/fragments/details-heading/${monitor.id}")
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
                        a(href = "/icmp-monitors") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = "ICMP",
                                icon = Icon.WAVE_SQUARE,
                                color = Color.ORANGE_LT
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
                    }
                }
            }
        }
    }
}
