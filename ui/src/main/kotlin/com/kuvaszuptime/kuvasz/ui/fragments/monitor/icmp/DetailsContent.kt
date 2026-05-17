package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun FlowContent.icmpMonitorDetailsContent(monitor: IcmpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) {
    div {
        id = "icmp-monitor-details-content"
        h2 { +Messages.uptimeBlockTitle() }
        detailsIcmpUptimeSummary(monitor, stats)
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
            id = "icmp-monitor-details-incidents"
            hx {
                get("/icmp-monitors/fragments/details-uptime-incidents/${monitor.id}")
                trigger {
                    load()
                    every(15.seconds)
                }
                onSwapReinitTooltips()
                swap(HtmxAttrs.Swap.innerHTML)
            }
        }
        // Latency and packet loss metrics
        if (monitor.metricsHistoryEnabled) {
            icmpDetailsMetricsBlock(monitor)
        }
    }
}
