package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun FlowContent.httpMonitorDetailsContent(monitor: HttpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) {
    div {
        id = "monitor-details-content"
        // Uptime summary
        h2 { +Messages.uptimeBlockTitle() }
        detailsUptimeSummary(monitor, stats)
        // Uptime incidents
        h3 {
            +Messages.incidents()
            span {
                classes(BADGE)
                +Messages.lastX(
                    Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS).formatAsSimpleInterval()
                )
            }
        }
        div {
            classes(ROW, ROW_CARDS, MB_3)
            id = "monitor-details-uptime-events"
            hx {
                get("/http-monitors/fragments/details-uptime-incidents/${monitor.id}")
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
                    +Messages.lastXHours(UIDefaults.HTTP_MONITOR_LATENCY_STATS_PERIOD_HOURS)
                }
            }
            detailsMetricsBlock(monitor)
        }
        // SSL check metrics
        if (monitor.sslCheckEnabled) {
            h2 { +Messages.sslBlockTitle() }
            detailsSSLSummary(monitor)
            // SSL incidents
            h3 {
                +Messages.incidents()
                span {
                    classes(BADGE)
                    +Messages.lastX(
                        Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS).formatAsSimpleInterval()
                    )
                }
            }
            div {
                classes(ROW, ROW_CARDS, MB_3)
                id = "monitor-details-ssl-events"
                hx {
                    get("/http-monitors/fragments/details-ssl-incidents/${monitor.id}")
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
