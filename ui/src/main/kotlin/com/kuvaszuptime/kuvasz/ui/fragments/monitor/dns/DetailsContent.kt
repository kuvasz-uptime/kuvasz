package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun FlowContent.dnsMonitorDetailsContent(monitor: DnsMonitorDetailsDto, stats: HistoricalUptimeStatsDto) {
    div {
        id = "dns-monitor-details-content"
        h2 {
            testId("uptime-block-title")
            +Messages.uptimeBlockTitle()
        }
        detailsDnsUptimeSummary(monitor, stats)
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
            id = "dns-monitor-details-incidents"
            hx {
                get("/dns-monitors/fragments/details-uptime-incidents/${monitor.id}")
                trigger {
                    load()
                    every(15.seconds)
                }
                onSwapReinitTooltips()
                swap(HtmxAttrs.Swap.innerHTML)
            }
        }
        // Latency metrics
        if (monitor.metricsHistoryEnabled) {
            dnsDetailsMetricsBlock(monitor)
        }
        // Resolved records snapshot (auto-refreshed; empty until drift detection records one). Without drift detection
        // there is never a snapshot, so the block is left out.
        if (monitor.driftDetectionEnabled) {
            div {
                id = "dns-monitor-details-snapshot"
                hx {
                    get("/dns-monitors/fragments/snapshot/${monitor.id}")
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
