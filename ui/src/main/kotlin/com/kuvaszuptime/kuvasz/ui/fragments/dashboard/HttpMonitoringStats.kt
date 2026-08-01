package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderHttpMonitoringStats(
    monitoringStats: HttpMonitoringStatsDto,
    downMonitors: List<HttpMonitorDetailsDto>,
    problematicSslMonitors: List<HttpMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        uptimeStatsSection(
            typeUiConfig = MonitorTypeUiConfig.HTTP,
            actualStats = monitoringStats.actual.uptimeStats,
            historyStats = monitoringStats.history.uptimeStats,
            downMonitors = downMonitors,
            columns = listOf(
                lastCheckColumn(),
                timestampColumn(Messages.nextCheck(), D_MD_TABLE_CELL) { it.nextUptimeCheck },
            ),
            // This is the first section of the dashboard, so it needs less room above it than the ones following it
            topMargin = MT_1,
            nameTooltip = { it.url.toString() },
        )
        sslStatsSection(monitoringStats.actual.sslStats, problematicSslMonitors)
    }

// SSL is checked by HTTP monitors only, so it gets a section of its own instead of a place in the shared one
private fun FlowContent.sslStatsSection(
    sslStats: HttpMonitoringStatsDto.ActualMonitoringStats.SslStats,
    problematicSslMonitors: List<HttpMonitorDetailsDto>,
) {
    div {
        classes(ROW, ROW_CARDS, BORDER_TOP, MT_6)
        statsSectionHeader(
            title = Messages.sslTitle(),
            lastIncident = null,
            icon = Icon.LOCK_CLOSED,
            colorClasses = setOf(BG_YELLOW_LT, TEXT_YELLOW_LT_FG)
        )
        // SSL summary
        statCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_CLOSED,
            iconBackground = BG_GREEN_LT,
            text = sslStats.valid.toString(),
            secondaryText = Messages.valid()
        )
        statCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_OPEN,
            iconBackground = BG_RED_LT,
            text = sslStats.invalid.toString(),
            secondaryText = Messages.invalid(),
        )
        statCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.TIMER,
            iconBackground = BG_YELLOW_LT,
            text = sslStats.willExpire.toString(),
            secondaryText = Messages.expiresSoon(),
        )
        statCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_QUESTION,
            iconBackground = BG_ORANGE_LT,
            text = sslStats.inProgress.toString(),
            secondaryText = Messages.inProgress(),
        )
        // SSL issues table
        monitorsWithIssuesBlock(
            monitors = problematicSslMonitors,
            typeUiConfig = MonitorTypeUiConfig.HTTP,
            noIssuesText = Messages.noSSLIssues(),
            statusCell = { monitor -> sslStatusOfMonitor(monitor, withTooltip = true) },
            columns = listOf(
                timestampColumn(Messages.lastCheck(), D_LG_TABLE_CELL) { it.lastSSLCheck },
                timestampColumn(Messages.nextCheck(), D_MD_TABLE_CELL) { it.nextSSLCheck },
            ),
            nameTooltip = { it.url.toString() },
        )
    }
}
