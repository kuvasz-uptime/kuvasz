package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*

internal fun FlowContent.httpMonitorDetailsContent(monitor: HttpMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorDetailsContent(
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        monitor = monitor,
        uptimeSummary = { detailsHttpUptimeSummary(monitor, stats) },
    ) {
        // Latency metrics
        if (monitor.latencyHistoryEnabled) {
            h2 {
                testId("latency-block-title")
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
            h2 {
                testId("ssl-block-title")
                +Messages.sslBlockTitle()
            }
            detailsSSLSummary(monitor)
            // SSL incidents
            incidentsHeading()
            autoRefreshedBlock(
                elementId = "http-monitor-details-ssl-events",
                path = MonitorTypeUiConfig.HTTP.fragmentPath("details-ssl-incidents/${monitor.id}"),
                cssClasses = setOf(ROW, ROW_CARDS, MB_3),
            )
        }
    }
