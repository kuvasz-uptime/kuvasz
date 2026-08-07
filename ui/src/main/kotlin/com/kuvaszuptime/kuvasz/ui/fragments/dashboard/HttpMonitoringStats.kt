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

fun renderHttpMonitoringStats(
    monitoringStats: HttpMonitoringStatsDto,
    downMonitors: List<HttpMonitorDetailsDto>,
    problematicSslMonitors: List<HttpMonitorDetailsDto>,
): String = renderStatsSectionOfType(monitoringStats.actual.uptimeStats) {
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

private val HttpMonitoringStatsDto.ActualMonitoringStats.SslStats.checkedMonitors: Int
    get() = valid + invalid + willExpire + inProgress

// SSL is checked by HTTP monitors only, so it gets a section of its own instead of a place in the shared one
private fun FlowContent.sslStatsSection(
    sslStats: HttpMonitoringStatsDto.ActualMonitoringStats.SslStats,
    problematicSslMonitors: List<HttpMonitorDetailsDto>,
) {
    if (sslStats.checkedMonitors == 0) return

    div {
        classes(ROW, ROW_CARDS, BORDER_TOP, MT_6)
        statsSectionHeader(
            title = Messages.sslTitle(),
            lastIncident = null,
            icon = Icon.LOCK_CLOSED,
            colorClasses = setOf(BG_YELLOW_LT, TEXT_YELLOW_LT_FG)
        )
        // SSL summary
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_CLOSED,
            iconBackground = BG_GREEN_LT,
            value = sslStats.valid.toLong(),
            secondaryText = Messages.valid()
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_OPEN,
            iconBackground = BG_RED_LT,
            value = sslStats.invalid.toLong(),
            secondaryText = Messages.invalid(),
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.TIMER,
            iconBackground = BG_YELLOW_LT,
            value = sslStats.willExpire.toLong(),
            secondaryText = Messages.expiresSoon(),
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.LOCK_QUESTION,
            iconBackground = BG_ORANGE_LT,
            value = sslStats.inProgress.toLong(),
            secondaryText = Messages.inProgress(),
        )
        // SSL issues table
        monitorsWithIssuesBlock(
            monitors = problematicSslMonitors,
            typeUiConfig = MonitorTypeUiConfig.HTTP,
            statusCell = { monitor -> sslStatusOfMonitor(monitor, withTooltip = true) },
            columns = listOf(
                timestampColumn(Messages.lastCheck(), D_LG_TABLE_CELL) { it.lastSSLCheck },
                timestampColumn(Messages.nextCheck(), D_MD_TABLE_CELL) { it.nextSSLCheck },
            ),
            blockTestId = SSL_ISSUES_BLOCK_TEST_ID,
            nameTooltip = { it.url.toString() },
        )
    }
}
