package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitoringStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import java.math.RoundingMode

private const val METRICS_PERIOD_DEFAULT_DAYS = 7L

fun renderMonitoringStats(
    monitoringStats: MonitoringStatsDto,
    downMonitors: List<MonitorDetailsDto>,
    problematicSslMonitors: List<MonitorDetailsDto>,
): String = createHTML(prettyPrint = false, xhtmlCompatible = false)
    .div {
        val lastIncidentText = monitoringStats.actual.uptimeStats.lastIncident?.let { last ->
            Messages.lastIncidentAgo(last.timeAgo())
        }
        lastIncidentText?.let { lastIncidentTimeago ->
            p {
                classes(TEXT_SECONDARY, TEXT_MUTED)
                +lastIncidentTimeago
            }
        }
        div {
            // Stats summary
            classes(ROW, ROW_CARDS)
            statCard(
                icon = Icon.HEART,
                iconBackground = BG_GREEN_LT,
                text = monitoringStats.actual.uptimeStats.up.toString(),
                secondaryText = Messages.up()
            )
            statCard(
                icon = Icon.HEART_BROKEN,
                iconBackground = BG_RED_LT,
                text = monitoringStats.actual.uptimeStats.down.toString(),
                secondaryText = Messages.down(),
            )
            statCard(
                icon = Icon.HEART_OFF,
                iconBackground = BG_CYAN_LT,
                text = monitoringStats.actual.uptimeStats.paused.toString(),
                secondaryText = Messages.paused(),
            )
            // Historical stats
            h3 {
                classes(MT_3, MB_0)
                +Messages.metricsFromTheLast(Messages.xDays(METRICS_PERIOD_DEFAULT_DAYS))
            }
            statCard(
                icon = Icon.ALERT_TRIANGLE,
                iconBackground = BG_RED_LT,
                text = monitoringStats.history.uptimeStats.incidents.toString(),
                secondaryText = Messages.incidents()
            )
            statCard(
                icon = Icon.BINOCULARS,
                iconBackground = BG_RED_LT,
                text = monitoringStats.history.uptimeStats.affectedMonitors.toString(),
                secondaryText = Messages.affectedMonitors(),
            )
            val uptimeRatioText = monitoringStats.history.uptimeStats.uptimeRatio?.let { ratio ->
                (ratio * 100.toDouble()).toBigDecimal().setScale(2, RoundingMode.FLOOR).toString() + "%"
            } ?: Messages.noData()
            statCard(
                icon = Icon.PERCENTAGE,
                iconBackground = BG_GREEN_LT,
                text = uptimeRatioText,
                secondaryText = Messages.uptimeRatio(),
            )
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
            classes(ROW, ROW_CARDS)
            h2 {
                classes(MT_6)
                +Messages.sslTitle()
            }
            // SSL summary
            statCard(
                icon = Icon.LOCK_CLOSED,
                iconBackground = BG_GREEN_LT,
                text = monitoringStats.actual.sslStats.valid.toString(),
                secondaryText = Messages.valid()
            )
            statCard(
                icon = Icon.LOCK_OPEN,
                iconBackground = BG_RED_LT,
                text = monitoringStats.actual.sslStats.invalid.toString(),
                secondaryText = Messages.invalid(),
            )
            statCard(
                icon = Icon.TIMER,
                iconBackground = BG_YELLOW_LT,
                text = monitoringStats.actual.sslStats.willExpire.toString(),
                secondaryText = Messages.expiresSoon(),
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

private fun FlowContent.statCard(
    icon: Icon,
    iconBackground: CSSClass,
    text: String,
    secondaryText: String,
) {
    div {
        classes(COL_MD_4)
        div {
            classes(CARD, CARD_SM)
            div {
                classes(CARD_BODY)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER)
                    div {
                        classes(COL_AUTO)
                        span {
                            classes(iconBackground, AVATAR)
                            icon(icon)
                        }
                    }
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(FW_BOLDER)
                            +text
                        }
                        div {
                            classes(TEXT_SECONDARY)
                            +secondaryText
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.downMonitorList(monitors: List<MonitorDetailsDto>) =
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
                        a(href = "/monitors/${monitor.id}") {
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

private fun FlowContent.problematicSSLMonitorList(monitors: List<MonitorDetailsDto>) =
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
                        a(href = "/monitors/${monitor.id}") {
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
