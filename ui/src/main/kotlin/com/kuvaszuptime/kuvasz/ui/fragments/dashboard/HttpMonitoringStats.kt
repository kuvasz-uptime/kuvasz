package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.OffsetDateTime

fun renderHttpMonitoringStats(
    monitoringStats: HttpMonitoringStatsDto,
    downMonitors: List<HttpMonitorDetailsDto>,
    problematicSslMonitors: List<HttpMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        div {
            // Stats summary
            classes(ROW, ROW_CARDS, BORDER_TOP, MT_1)
            statsSectionHeader(
                title = "HTTP",
                lastIncident = monitoringStats.actual.uptimeStats.lastIncident,
                icon = Icon.WORLD,
                colorClasses = setOf(BG_BLUE_LT, TEXT_BLUE_LT_FG)
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.HEART,
                iconBackground = BG_GREEN_LT,
                text = monitoringStats.actual.uptimeStats.up.toString(),
                secondaryText = Messages.up()
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.HEART_BROKEN,
                iconBackground = BG_RED_LT,
                text = monitoringStats.actual.uptimeStats.down.toString(),
                secondaryText = Messages.down(),
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.HEART_OFF,
                iconBackground = BG_CYAN_LT,
                text = monitoringStats.actual.uptimeStats.paused.toString(),
                secondaryText = Messages.paused(),
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.TOOL,
                iconBackground = BG_GRAY_300,
                text = monitoringStats.actual.uptimeStats.inMaintenance.toString(),
                secondaryText = Messages.maintenance(),
            )
            // Historical stats
            h3 {
                classes(MT_3, MB_0)
                +Messages.metrics()
                inlineBadge(
                    Messages.lastXDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)
                )
            }
            incidentsStatsCards(cssClasses = setOf(COL_6, COL_MD_3), monitoringStats.history.uptimeStats)
            affectedMonitorsStatsCards(cssClasses = setOf(COL_6, COL_MD_3), monitoringStats.history.uptimeStats)
            uptimeRatioStatsCards(cssClasses = setOf(COL_6, COL_MD_3), monitoringStats.history.uptimeStats)
            totalDowntimeStatsCards(cssClasses = setOf(COL_6, COL_MD_3), monitoringStats.history.uptimeStats)
            // Down monitors table
            h3 {
                classes(MT_3, MB_0)
                +Messages.monitorsWithIssues()
            }
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    if (downMonitors.isNotEmpty()) {
                        div {
                            classes(CARD_TABLE, TABLE_RESPONSIVE)
                            downMonitorList(downMonitors)
                        }
                    } else {
                        div {
                            classes(CARD_BODY)
                            p {
                                classes(TEXT_SECONDARY, TEXT_CENTER)
                                +Messages.noUptimeIssues()
                            }
                        }
                    }
                }
            }
        }
        // SSL
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
                text = monitoringStats.actual.sslStats.valid.toString(),
                secondaryText = Messages.valid()
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.LOCK_OPEN,
                iconBackground = BG_RED_LT,
                text = monitoringStats.actual.sslStats.invalid.toString(),
                secondaryText = Messages.invalid(),
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.TIMER,
                iconBackground = BG_YELLOW_LT,
                text = monitoringStats.actual.sslStats.willExpire.toString(),
                secondaryText = Messages.expiresSoon(),
            )
            statCard(
                cssClasses = setOf(COL_6, COL_MD_3),
                icon = Icon.LOCK_QUESTION,
                iconBackground = BG_ORANGE_LT,
                text = monitoringStats.actual.sslStats.inProgress.toString(),
                secondaryText = Messages.inProgress(),
            )
            // SSL issues table
            h3 {
                classes(MT_3, MB_0)
                +Messages.monitorsWithIssues()
            }
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    if (problematicSslMonitors.isNotEmpty()) {
                        div {
                            classes(CARD_TABLE, TABLE_RESPONSIVE)
                            problematicSSLMonitorList(problematicSslMonitors)
                        }
                    } else {
                        div {
                            classes(CARD_BODY)
                            p {
                                classes(TEXT_SECONDARY, TEXT_CENTER)
                                +Messages.noSSLIssues()
                            }
                        }
                    }
                }
            }
        }
    }

private fun FlowContent.downMonitorList(monitors: List<HttpMonitorDetailsDto>) =
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
                    classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                    +Messages.lastCheck()
                }
                th {
                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                    +Messages.nextCheck()
                }
            }
        }
        tbody {
            monitors.forEach { monitor ->
                tr {
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
                        classes(TEXT_NOWRAP, D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                        span {
                            monitor.lastUptimeCheck?.let { lastCheck ->
                                tooltip(title = lastCheck.toDateTimeString())
                                +lastCheck.timeAgo()
                            }
                        }
                    }
                    td {
                        classes(TEXT_NOWRAP, D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                        span {
                            monitor.nextUptimeCheck?.let { nextCheck ->
                                tooltip(title = nextCheck.toDateTimeString())
                                +nextCheck.timeAgo()
                            }
                        }
                    }
                }
            }
        }
    }

private fun FlowContent.problematicSSLMonitorList(monitors: List<HttpMonitorDetailsDto>) =
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
                    classes(D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                    +Messages.lastCheck()
                }
                th {
                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                    +Messages.nextCheck()
                }
            }
        }
        tbody {
            monitors.forEach { monitor ->
                tr {
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
                        sslStatusOfMonitor(monitor, withTooltip = true)
                    }
                    td {
                        classes(TEXT_NOWRAP, D_NONE, D_LG_TABLE_CELL, TEXT_CENTER)
                        span {
                            monitor.lastSSLCheck?.let { lastCheck ->
                                tooltip(title = lastCheck.toDateTimeString())
                                +lastCheck.timeAgo()
                            }
                        }
                    }
                    td {
                        classes(TEXT_NOWRAP, D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                        span {
                            monitor.nextSSLCheck?.let { nextCheck ->
                                tooltip(title = nextCheck.toDateTimeString())
                                +nextCheck.timeAgo()
                            }
                        }
                    }
                }
            }
        }
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
