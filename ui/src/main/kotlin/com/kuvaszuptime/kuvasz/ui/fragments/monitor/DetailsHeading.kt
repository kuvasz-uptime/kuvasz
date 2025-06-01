package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

fun renderMonitorDetailsHeading(monitor: MonitorDetailsDto): String =
    buildString { appendHTML().div { monitorDetailsHeading(monitor) } }

private const val MONITOR_URL_MAX_LENGTH = 60

internal fun FlowContent.monitorDetailsHeading(monitor: MonitorDetailsDto) {
    div {
        id = "monitor-detail-heading"
        classes(COL_AUTO)
        hx {
            get("/fragments/monitors/${monitor.id}/details-heading")
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
                        a(href = "#monitor-details-ssl-summary") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE, TEXT_WRAP, TEXT_BREAK)
                            sslStatusOfMonitor(monitor, withTooltip = false)
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            +monitor.url.toString().abbreviate(MONITOR_URL_MAX_LENGTH)
                        }
                    }
                }
            }
        }
    }
}
