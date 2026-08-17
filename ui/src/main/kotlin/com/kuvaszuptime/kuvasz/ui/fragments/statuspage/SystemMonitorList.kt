package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageTcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.WithLatency
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*

// The filter keys are prefixed ("c:<name>" for a real category, "u" for the uncategorized section), so a real
// category name can never collide with the uncategorized key
private const val CATEGORY_KEY_PREFIX = "c:"
private const val UNCATEGORIZED_KEY = "u"

fun FlowContent.systemStatusMonitorList(pageData: StatusPageDataDto) {
    val categories = pageData.monitors
        .mapNotNull { it.category }
        .distinct()
        .sortedBy { it.lowercase() }

    if (categories.isEmpty()) {
        // No categories at all -> keep the plain, uncategorized list
        monitorCardGrid(pageData.monitors)
    } else {
        categorizedMonitorList(pageData.monitors, categories)
    }
}

private fun FlowContent.categorizedMonitorList(
    monitors: List<StatusPageMonitorDetailsDto>,
    categories: List<String>,
) {
    val uncategorized = monitors.filter { it.category == null }
    val filterKeys = categories.map { CATEGORY_KEY_PREFIX + it } +
        if (uncategorized.isNotEmpty()) listOf(UNCATEGORIZED_KEY) else emptyList()

    div {
        xData("statusPageCategoryFilter(${filterKeys.asJsonString()})")
        // Category filter bar with the quick all/none selectors
        div {
            classes(D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, GAP_2, MB_4)
            testId("category-filter")
            span {
                classes(TEXT_SECONDARY, ME_2)
                +Messages.statusPageCategoryFilterLabel()
            }
            filterKeys.forEach { key ->
                button(type = ButtonType.button) {
                    classes(BTN, BTN_SM)
                    attributes["data-category"] = key
                    testId("category-chip")
                    xBindClass(
                        "isSelected(\$el.dataset.category) " +
                            "? '${BTN_PRIMARY.className}' : '${BTN_GHOST_SECONDARY.className}'"
                    )
                    xOnClick("toggle(\$el.dataset.category)")
                    +key.categoryLabel()
                }
            }
            span {
                classes(MS_AUTO)
                div {
                    classes(BTN_LIST)
                    button(type = ButtonType.button) {
                        classes(BTN, BTN_SM, BTN_GHOST_SECONDARY)
                        testId("category-select-all")
                        xBindDisabled("allSelected")
                        xOnClick("selectAll()")
                        +Messages.statusPageCategoryFilterAll()
                    }
                    button(type = ButtonType.button) {
                        classes(BTN, BTN_SM, BTN_GHOST_SECONDARY)
                        testId("category-select-none")
                        xBindDisabled("noneSelected")
                        xOnClick("selectNone()")
                        +Messages.statusPageCategoryFilterNone()
                    }
                }
            }
        }
        // One section per category, the uncategorized monitors go to the end
        val sections = categories.map { category ->
            CATEGORY_KEY_PREFIX + category to monitors.filter { monitor -> monitor.category == category }
        } + if (uncategorized.isNotEmpty()) listOf(UNCATEGORIZED_KEY to uncategorized) else emptyList()
        sections.forEach { (key, sectionMonitors) ->
            div {
                attributes["data-category"] = key
                xShow("isSelected(\$el.dataset.category)")
                testId("category-section")
                div {
                    classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_3)
                    h2 {
                        classes(MB_0, ME_2)
                        +key.categoryLabel()
                    }
                    div {
                        classes(MS_AUTO)
                        categoryStatusBadge(SystemStatus.fromMonitors(sectionMonitors))
                    }
                }
                monitorCardGrid(sectionMonitors)
            }
        }
    }
}

private fun String.categoryLabel(): String =
    if (this == UNCATEGORIZED_KEY) Messages.statusPageUncategorized() else removePrefix(CATEGORY_KEY_PREFIX)

private fun FlowContent.categoryStatusBadge(status: SystemStatus) {
    span {
        classes(STATUS, status.statusBadgeColor())
        testId("category-status-badge")
        tooltip(status.description())
        +status.title()
    }
}

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
