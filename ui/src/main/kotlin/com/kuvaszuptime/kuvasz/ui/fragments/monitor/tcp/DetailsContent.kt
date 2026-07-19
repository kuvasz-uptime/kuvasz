package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.iodesystems.htmx.Htmx.Companion.hx
import com.iodesystems.htmx.HtmxAttrs
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun FlowContent.tcpMonitorDetailsContent(monitor: TcpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) {
    div {
        id = "tcp-monitor-details-content"
        h2 {
            testId("uptime-block-title")
            +Messages.uptimeBlockTitle()
        }
        detailsTcpUptimeSummary(monitor, stats)
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
            id = "tcp-monitor-details-incidents"
            hx {
                get("/tcp-monitors/fragments/details-uptime-incidents/${monitor.id}")
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
            tcpDetailsMetricsBlock(monitor)
        }
    }
}
