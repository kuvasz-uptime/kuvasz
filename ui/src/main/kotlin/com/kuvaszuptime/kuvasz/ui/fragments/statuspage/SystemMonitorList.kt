package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.CategoryStatusDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageTcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.WithLatency
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*

fun FlowContent.systemStatusMonitorList(pageData: StatusPageDataDto) {
    if (pageData.categoryStatus.isEmpty()) {
        // None of the monitors is categorized -> keep the plain, ungrouped list
        monitorCardGrid(pageData.monitors)
    } else {
        categorizedMonitorList(pageData.monitors, pageData.categoryStatus)
    }
}

private fun FlowContent.categorizedMonitorList(
    monitors: List<StatusPageMonitorDetailsDto>,
    categoryStatus: List<CategoryStatusDto>,
) {
    // The sections are anchored by their index, because a category is free-form and may not survive slugification
    div {
        classes(ROW, ROW_CARDS, MB_4)
        testId("category-cards")
        categoryStatus.forEachIndexed { index, category ->
            div {
                classes(COL_SM_6, COL_LG_3)
                categoryCard(index, category)
            }
        }
    }
    categoryStatus.forEachIndexed { index, category ->
        div {
            id = categoryAnchorId(index)
            classes(CATEGORY_SECTION)
            testId("category-section")
            h2 {
                classes(MB_3)
                +category.label()
            }
            monitorCardGrid(monitors.filter { it.category == category.category })
        }
    }
}

/**
 * A compact card of a category, showing its aggregated status and linking to the section of its monitors.
 */
private fun FlowContent.categoryCard(index: Int, category: CategoryStatusDto) {
    a(href = "#${categoryAnchorId(index)}") {
        classes(CARD, CARD_SM, CARD_LINK, CARD_LINK_POP)
        testId("category-card")
        div {
            classes(CARD_BODY)
            div {
                classes(D_FLEX, ALIGN_ITEMS_CENTER)
                span {
                    classes(ME_3, category.status.color())
                    icon(category.status.icon())
                }
                div {
                    classes(TEXT_TRUNCATE)
                    div {
                        classes(FW_MEDIUM, TEXT_TRUNCATE)
                        +category.label()
                    }
                    div {
                        classes(TEXT_SECONDARY, TEXT_TRUNCATE)
                        +category.status.title()
                    }
                }
            }
        }
    }
}

private fun categoryAnchorId(index: Int) = "category-$index"

private fun CategoryStatusDto.label(): String = category ?: Messages.statusPageOtherCategory()

private fun FlowContent.monitorCardGrid(monitors: List<StatusPageMonitorDetailsDto>) {
    div {
        classes(CSSClass.ROW)
        // DOWN monitors are more important, sort by status (D < U), then by name, ignoring its casing
        monitors
            .sortedWith(compareBy({ it.uptimeStatus?.name.toString() }, { it.name.lowercase() }))
            .forEach { monitor ->
                div {
                    classes(COL_MD_6, MB_3)
                    // Monitor card
                    div {
                        classes(CARD)
                        testId("status-monitor-card")
                        // Card status indicator
                        div {
                            classes(CARD_STATUS_START, monitor.uptimeStatus.cardStatusClass(monitor.inMaintenance))
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
                                    uptimeBadgeOfStatus(monitor.uptimeStatus, monitor.inMaintenance)
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

                                    is StatusPageTcpMonitorDetailsDto -> {
                                        val lastCheckText = monitor.lastCheck?.timeAgo() ?: Messages.noData()
                                        +"${Messages.lastCheck()}: $lastCheckText"
                                    }

                                    is StatusPageDnsMonitorDetailsDto -> {
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
