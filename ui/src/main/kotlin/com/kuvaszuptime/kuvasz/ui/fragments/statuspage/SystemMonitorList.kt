package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.WithLatency
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*

fun FlowContent.systemStatusMonitorList(pageData: StatusPageDataDto) {
    div {
        classes(CSSClass.ROW)
        // DOWN monitors are more important, sort by status (D < U), then by name
        pageData.monitors
            .sortedBy { it.uptimeStatus?.name + it.name }
            .forEach { monitor ->
                div {
                    classes(COL_MD_6, MB_3)
                    // Monitor card
                    div {
                        classes(CARD)
                        testId("status-monitor-card")
                        // Card status indicator
                        div {
                            classes(CARD_STATUS_START, monitor.uptimeStatus.cardStatusClass())
                        }
                        div {
                            classes(CARD_BODY)
                            div {
                                classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_3)
                                // Monitor name on the left
                                h3 {
                                    classes(TEXT_SECONDARY)
                                    +monitor.name
                                }
                                // Monitor status on the right side
                                div {
                                    classes(MS_AUTO)
                                    uptimeBadgeOfStatus(monitor.uptimeStatus)
                                }
                            }
                            // Uptime percentage and average response time row
                            div {
                                classes(D_FLEX, ALIGN_ITEMS_BASELINE)
                                // Uptime percentage on the left
                                div {
                                    classes(H1, MB_3, ME_2)
                                    +(monitor.uptimeRatio?.formatAsPercentage() ?: Messages.noData())
                                }
                                // Average response time on the right if available
                                val avgLatency: Int? = if (monitor is WithLatency) {
                                    monitor.averageLatencyInMs
                                } else null
                                avgLatency?.let {
                                    div {
                                        classes(MS_AUTO)
                                        span {
                                            classes(TEXT_SECONDARY, D_INLINE_FLEX, ALIGN_ITEMS_CENTER, LH_1)
                                            +Messages.avgResponseTime(avgLatency)
                                        }
                                    }
                                }
                            }
                            // Tracking blocks
                            div {
                                classes(MT_2)
                                div {
                                    classes(TRACKING)
                                    monitor.uptimeStatusHistory.forEach { trackingBlock(it) }
                                }
                            }
                            // Last check time / last heartbeat
                            div {
                                classes(MT_4, TEXT_MUTED)
                                when (monitor) {
                                    is StatusPageHttpMonitorDetailsDto -> {
                                        val lastCheckText = monitor.lastCheck?.timeAgo() ?: Messages.noData()
                                        +"${Messages.lastCheck()}: $lastCheckText"
                                    }

                                    is StatusPagePushMonitorDetailsDto -> {
                                        val lastHeartbeatText = monitor.lastHeartbeat?.timeAgo() ?: Messages.noData()
                                        +"${Messages.lastHeartbeat()}: $lastHeartbeatText"
                                    }

                                    is StatusPageIcmpMonitorDetailsDto -> {
                                        val lastCheckText = monitor.lastCheck?.timeAgo() ?: Messages.noData()
                                        +"${Messages.lastCheck()}: $lastCheckText"
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

private fun FlowContent.trackingBlock(statusHistoryDto: StatusHistoryDto) {
    div {
        val outageCnt = statusHistoryDto.outageCnt
        val colorClass = when {
            outageCnt == null -> TEXT_MUTED
            outageCnt == 0 -> BG_SUCCESS
            outageCnt > 0 -> BG_DANGER
            else -> TEXT_MUTED
        }
        classes(TRACKING_BLOCK, colorClass)
        val tooltipText = statusHistoryDto.outageCnt?.let {
            Messages.incidentCount(
                statusHistoryDto.date.toDateTimeString(),
                statusHistoryDto.outageCnt.toString(),
            )
        } ?: Messages.noData()
        tooltip(tooltipText)
    }
}
