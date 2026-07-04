package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.durationBetween
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderPushUptimeSummary(monitor: PushMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsPushUptimeSummary(monitor, stats) } }

fun FlowContent.detailsPushUptimeSummary(monitor: PushMonitorDetailsDto, stats: HistoricalUptimeStatsDto) {
    div {
        id = "push-monitor-details-uptime-summary"
        hx { swapOob() }

        div {
            classes(ROW, ROW_CARDS, MB_3)
            div {
                classes(COL_MD_4)
                div {
                    classes(CARD)
                    div {
                        classes(CARD_STAMP)
                        div {
                            classes(
                                mutableSetOf(CARD_STAMP_ICON).addIfNotNull(getUptimeCardStatusClass(monitor))
                            )
                            icon(getUptimeCardIcon(monitor))
                        }
                    }
                    div {
                        classes(
                            mutableSetOf(CARD_STATUS_START).addIfNotNull(getUptimeCardStatusClass(monitor))
                        )
                    }
                    div {
                        classes(CARD_BODY)
                        if (monitor.enabled) {
                            monitor.uptimeStatusStartedAt?.let { uptimeStatusStartedAt ->
                                div {
                                    classes(SUBHEADER)
                                    +Messages.currentlyFor(monitor.uptimeStatus?.literal.orEmpty())
                                }
                                h4 {
                                    classes(M_0)
                                    +uptimeStatusStartedAt.durationBetween()
                                }
                            } ?: run {
                                div {
                                    classes(SUBHEADER)
                                    +Messages.monitorWasJustCreated()
                                }
                                h4 {
                                    classes(M_0)
                                    +Messages.waitingForFirstHeartbeat()
                                }
                            }
                        } else {
                            div {
                                classes(SUBHEADER)
                                +Messages.currentUptimeStatus()
                            }
                            h4 {
                                classes(M_0)
                                +Messages.monitorIsPaused()
                            }
                        }
                    }
                }
            }

            div {
                classes(COL_MD_4)
                div {
                    classes(CARD)
                    div {
                        classes(CARD_BODY)
                        div {
                            classes(SUBHEADER)
                            +Messages.lastHeartbeat()
                        }
                        monitor.lastHeartbeat?.let { lastHeartbeat ->
                            h4 {
                                classes(M_0)
                                tooltip(lastHeartbeat.toDateTimeString())
                                +lastHeartbeat.timeAgo()
                            }
                        } ?: run {
                            h4 {
                                classes(M_0)
                                +Messages.waitingForFirstHeartbeat()
                            }
                        }
                    }
                }
            }

            div {
                classes(COL_MD_4)
                div {
                    classes(CARD)
                    div {
                        classes(CARD_BODY)
                        div {
                            classes(SUBHEADER)
                            +Messages.heartbeatExpected()
                        }
                        h4 {
                            classes(M_0)
                            if (monitor.enabled && monitor.nextExpectedHeartbeat != null) {
                                +monitor.nextExpectedHeartbeat?.timeAgo().orEmpty()
                            } else if (monitor.enabled) {
                                +Messages.waitingForFirstHeartbeat()
                            } else {
                                +Messages.monitorIsPaused()
                            }
                        }
                    }
                }
            }
        }
        div {
            classes(ROW, ROW_CARDS, MB_3)
            // Historical stats
            h3 {
                classes(MT_3, MB_0)
                +Messages.metrics()
                inlineBadge(
                    Messages.lastXDays(UIDefaults.PUSH_MONITOR_UPTIME_STATS_PERIOD_DAYS)
                )
            }
            incidentsStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
            uptimeRatioStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
            totalDowntimeStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
        }
    }
}
