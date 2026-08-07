package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.OffsetDateTime

internal const val UPTIME_ISSUES_BLOCK_TEST_ID = "uptime-issues-block"
internal const val SSL_ISSUES_BLOCK_TEST_ID = "ssl-issues-block"

internal fun renderStatsSectionOfType(actualStats: ActualUptimeStats, section: DIV.() -> Unit): String =
    if (actualStats.total == 0) {
        ""
    } else {
        createHTML(prettyPrint = false, xhtmlCompatible = false).div(block = section)
    }

internal fun FlowContent.statsSectionHeader(
    title: String,
    icon: Icon,
    colorClasses: Set<CSSClass>,
    lastIncident: OffsetDateTime?,
) =
    div {
        h2 {
            classes(MT_2)
            span {
                classes(colorClasses.plus(listOf(BADGE, ME_2)))
                icon(icon)
            }
            +title
        }
        val lastIncidentText = lastIncident?.let { last ->
            Messages.lastIncidentAgo(last.timeAgo())
        }
        lastIncidentText?.let { lastIncidentTimeago ->
            p {
                classes(TEXT_SECONDARY, TEXT_MUTED, MT_1)
                +lastIncidentTimeago
            }
        }
    }

/** A read-only table of the monitors a dashboard section wants to call out, e.g. the ones that are down. */
private fun <T : MonitorDetailsDto> FlowContent.monitorIssuesTable(
    monitors: List<T>,
    typeUiConfig: MonitorTypeUiConfig,
    statusCell: FlowContent.(T) -> Unit,
    columns: List<MonitorListColumn<T>>,
    nameTooltip: (T) -> String?,
) =
    table {
        classes(CSSClass.TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
        thead {
            tr {
                th { +Messages.name() }
                th {
                    classes(TEXT_CENTER)
                    +Messages.status()
                }
                columns.forEach { column ->
                    th {
                        classes(column.headerClasses)
                        +column.header
                    }
                }
            }
        }
        tbody {
            monitors.forEach { monitor ->
                tr {
                    monitorNameCell(monitor, typeUiConfig, nameTooltip(monitor))
                    td {
                        classes(TEXT_CENTER)
                        statusCell(this, monitor)
                    }
                    columns.forEach { column ->
                        td {
                            classes(column.cellClasses)
                            column.cell(this, monitor)
                        }
                    }
                }
            }
        }
    }

internal fun <T : MonitorDetailsDto> FlowContent.monitorsWithIssuesBlock(
    monitors: List<T>,
    typeUiConfig: MonitorTypeUiConfig,
    statusCell: FlowContent.(T) -> Unit,
    columns: List<MonitorListColumn<T>>,
    blockTestId: String,
    nameTooltip: (T) -> String? = { null },
) {
    if (monitors.isEmpty()) return

    h3 {
        classes(MT_3, MB_0)
        testId(blockTestId)
        +Messages.monitorsWithIssues()
    }
    div {
        classes(COL_12)
        div {
            classes(CARD)
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                monitorIssuesTable(monitors, typeUiConfig, statusCell, columns, nameTooltip)
            }
        }
    }
}

/**
 * The uptime section a monitor type contributes to the dashboard: the current up/down/paused/maintenance counts, the
 * historical stats of the dashboard's period and the monitors that are currently having issues.
 */
internal fun <T : MonitorDetailsDto> FlowContent.uptimeStatsSection(
    typeUiConfig: MonitorTypeUiConfig,
    actualStats: ActualUptimeStats,
    historyStats: HistoricalUptimeStatsDto,
    downMonitors: List<T>,
    columns: List<MonitorListColumn<T>>,
    // HTTP opens the dashboard, so it sits closer to what is above it than the sections following it
    topMargin: CSSClass = MT_6,
    nameTooltip: (T) -> String? = { null },
) {
    div {
        // Stats summary
        classes(ROW, ROW_CARDS, BORDER_TOP, topMargin)
        statsSectionHeader(
            title = typeUiConfig.dashboardTitle,
            lastIncident = actualStats.lastIncident,
            icon = typeUiConfig.icon,
            colorClasses = setOf(typeUiConfig.color.bgColor, typeUiConfig.color.textColor),
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.HEART,
            iconBackground = BG_GREEN_LT,
            value = actualStats.up.toLong(),
            secondaryText = Messages.up()
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.HEART_BROKEN,
            iconBackground = BG_RED_LT,
            value = actualStats.down.toLong(),
            secondaryText = Messages.down(),
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.HEART_OFF,
            iconBackground = BG_CYAN_LT,
            value = actualStats.paused.toLong(),
            secondaryText = Messages.paused(),
        )
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.TOOL,
            iconBackground = BG_GRAY_300,
            value = actualStats.inMaintenance.toLong(),
            secondaryText = Messages.maintenance(),
        )
        // Historical stats
        h3 {
            classes(MT_3, MB_0)
            +Messages.metrics()
            inlineBadge(Messages.lastXDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS))
        }
        incidentsStatsCards(cssClasses = setOf(COL_6, COL_MD_3), historyStats)
        numericStatCard(
            cssClasses = setOf(COL_6, COL_MD_3),
            icon = Icon.BINOCULARS,
            iconBackground = BG_RED_LT,
            value = historyStats.affectedMonitors.toLong(),
            secondaryText = Messages.affectedMonitors(),
        )
        uptimeRatioStatsCards(cssClasses = setOf(COL_6, COL_MD_3), historyStats)
        totalDowntimeStatsCards(cssClasses = setOf(COL_6, COL_MD_3), historyStats)
        // Down monitors table
        monitorsWithIssuesBlock(
            monitors = downMonitors,
            typeUiConfig = typeUiConfig,
            statusCell = { monitor -> uptimeBadgeOfMonitor(monitor, withTooltip = true) },
            columns = columns,
            blockTestId = UPTIME_ISSUES_BLOCK_TEST_ID,
            nameTooltip = nameTooltip,
        )
    }
}

/** The column of a dashboard table showing when a monitor was last checked. */
internal fun <T : MonitorDetailsDto> lastCheckColumn() =
    timestampColumn<T>(Messages.lastCheck(), D_LG_TABLE_CELL) { it.lastUptimeCheck }
