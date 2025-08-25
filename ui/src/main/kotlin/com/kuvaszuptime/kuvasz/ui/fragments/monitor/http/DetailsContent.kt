package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

internal fun FlowContent.httpMonitorDetailsContent(monitor: HttpMonitorDetailsDto) {
    div {
        id = "monitor-details-content"
        // Uptime summary
        h2 { +Messages.uptimeBlockTitle() }
        detailsUptimeSummary(monitor)
        // Uptime events
        h3 { +Messages.recentEventsBlockTitle() }
        div {
            classes(ROW, ROW_CARDS, MB_3)
            id = "monitor-details-uptime-events"
            hx {
                get("/http-monitors/fragments/details-uptime-events/${monitor.id}")
                trigger {
                    load()
                    every(15.seconds)
                }
                onSwapReinitTooltips()
                swap(HtmxAttrs.Swap.innerHTML)
            }
        }
        // Latency metrics
        if (monitor.latencyHistoryEnabled) {
            h2 {
                +Messages.latencyBlockTitle()
                span {
                    classes(BADGE)
                    +Messages.latencyBlockSubtitle()
                }
            }
            detailsLatencyBlock(monitor)
        }
        // SSL check metrics
        if (monitor.sslCheckEnabled) {
            h2 { +Messages.sslBlockTitle() }
            detailsSSLSummary(monitor)
            h3 { +Messages.recentEventsBlockTitle() }
            div {
                classes(ROW, ROW_CARDS, MB_3)
                id = "monitor-details-ssl-events"
                hx {
                    get("/http-monitors/fragments/details-ssl-events/${monitor.id}")
                    trigger {
                        load()
                        every(15.seconds)
                    }
                    onSwapReinitTooltips()
                    swap(HtmxAttrs.Swap.innerHTML)
                }
            }
        }
    }
}

fun renderHttpMonitorList(monitors: List<HttpMonitorDetailsDto>, isReadOnlyMode: Boolean): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        if (monitors.isNotEmpty()) {
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                table {
                    classes(CSSClass.TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                    thead {
                        tr {
                            th { +Messages.name() }
                            th {
                                classes(TEXT_CENTER)
                                +Messages.status()
                            }
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                +"SSL"
                            }
                            th {
                                classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                +Messages.changed()
                            }
                            th {
                                classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                +Messages.nextUptimeCheck()
                            }
                            if (!isReadOnlyMode) {
                                th {
                                    classes(TEXT_CENTER)
                                    +Messages.actions()
                                }
                            }
                        }
                    }
                    tbody {
                        monitors.forEach { monitor ->
                            tr {
                                xData("monitorList(${monitor.id}, ${monitor.enabled})")
                                td {
                                    a(href = "/http-monitors/${monitor.id}") {
                                        classes(TEXT_RESET)
                                        span {
                                            classes(TEXT_WRAP, TEXT_BREAK)
                                            tooltip(title = monitor.url.toString(), location = TooltipLocation.RIGHT)
                                            +monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH)
                                        }
                                    }
                                }
                                td {
                                    classes(TEXT_CENTER)
                                    uptimeBadgeOfMonitor(monitor, withTooltip = true)
                                }
                                td {
                                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                    sslStatusOfMonitor(monitor, withTooltip = true)
                                }
                                td {
                                    classes(TEXT_NOWRAP, D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                    span {
                                        monitor.uptimeStatusStartedAt?.let { startedAt ->
                                            tooltip(title = startedAt.toDateTimeString())
                                            +startedAt.timeAgo()
                                        }
                                    }
                                }
                                td {
                                    classes(TEXT_NOWRAP, D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                                    span {
                                        monitor.nextUptimeCheck?.let { nextCheck ->
                                            tooltip(title = nextCheck.toDateTimeString())
                                            +nextCheck.timeAgo()
                                        }
                                    }
                                }
                                if (!isReadOnlyMode) {
                                    val deleteModalId = "delete-monitor-modal-${monitor.id}"
                                    td {
                                        classes(TEXT_CENTER)
                                        div {
                                            classes(FLEX_NOWRAP, BTN_GROUP)
                                            val toggleIcon = if (monitor.enabled) Icon.PAUSE else Icon.PLAY
                                            compactIconButton(toggleIcon) {
                                                xBindDisabled("isRequestLoading")
                                                xOnClick("toggleMonitor()")
                                            }
                                            compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                                                xBindDisabled("isRequestLoading")
                                                modalOpener(deleteModalId)
                                            }
                                        }
                                        deleteMonitorModal(modalId = deleteModalId, monitorName = monitor.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            div {
                classes(CARD_BODY)
                p {
                    classes(TEXT_SECONDARY, TEXT_CENTER)
                    +Messages.noMonitors()
                }
            }
        }
    }
