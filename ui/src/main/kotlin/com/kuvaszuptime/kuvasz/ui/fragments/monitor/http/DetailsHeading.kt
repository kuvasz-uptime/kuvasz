package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

fun renderHttpMonitorDetailsHeading(monitor: HttpMonitorDetailsDto): String =
    buildString { appendHTML().div { httpMonitorDetailsHeading(monitor) } }

private const val MONITOR_URL_MAX_LENGTH = 40

internal fun FlowContent.httpMonitorDetailsHeading(monitor: HttpMonitorDetailsDto) {
    div {
        id = "http-monitor-detail-heading"
        classes(COL_AUTO)
        hx {
            get("/http-monitors/fragments/details-heading/${monitor.id}")
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
                        a(href = "/http-monitors") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = "HTTP & SSL",
                                icon = Icon.WORLD,
                                color = Color.BLUE_LT
                            )
                        }
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
                                        text = monitorUrl.abbreviate(MONITOR_URL_MAX_LENGTH),
                                        icon = Icon.EXTERNAL_LINK,
                                        color = Color.DEFAULT,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
