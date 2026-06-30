package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderPushMonitoringStats(
    monitoringStats: PushMonitoringStatsDto,
    downMonitors: List<PushMonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        div {
            // Stats summary
            classes(ROW, ROW_CARDS, BORDER_TOP, MT_6)
            statsSectionHeader(
                title = "Push",
                lastIncident = monitoringStats.actual.uptimeStats.lastIncident,
                icon = Icon.HEARTBEAT,
                colorClasses = setOf(BG_RED_LT, TEXT_RED_LT_FG)
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
    }

private fun FlowContent.downMonitorList(monitors: List<PushMonitorDetailsDto>) =
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
                    +Messages.lastHeartbeat()
                }
            }
        }
        tbody {
            monitors.forEach { monitor ->
                tr {
                    td {
                        a(href = "/push-monitors/${monitor.id}") {
                            classes(TEXT_RESET)
                            span {
                                classes(TEXT_WRAP, TEXT_BREAK)
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
                            monitor.lastHeartbeat?.let { lastHeartbeat ->
                                tooltip(title = lastHeartbeat.toDateTimeString())
                                +lastHeartbeat.timeAgo()
                            }
                        }
                    }
                }
            }
        }
    }
