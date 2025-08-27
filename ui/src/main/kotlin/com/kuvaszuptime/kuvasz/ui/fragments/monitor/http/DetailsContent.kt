package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
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
