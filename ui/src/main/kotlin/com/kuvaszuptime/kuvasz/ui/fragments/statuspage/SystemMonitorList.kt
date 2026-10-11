package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.CategoryStatusDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
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
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

fun FlowContent.systemStatusMonitorList(pageData: StatusPageDataDto) {
    // The page can opt out of the grouping even when its monitors are categorized: the categories still select them
    // and their aggregated statuses are still calculated, they are just not revealed to the visitors here
    if (pageData.categoryStatus.isEmpty() || !pageData.displayCategories) {
        monitorCardGrid(pageData.monitors, pageData.historyDays)
    } else {
        categorizedMonitorList(pageData.monitors, pageData.categoryStatus, pageData.historyDays)
    }
}

private fun FlowContent.categorizedMonitorList(
    monitors: List<StatusPageMonitorDetailsDto>,
    categoryStatus: List<CategoryStatusDto>,
    historyDays: Int,
) {
    val monitorsByCategory = categoryStatus.map { category ->
        category to monitors.filter { it.category == category.category }
    }
    // The sections are anchored by their index, because a category is free-form and may not survive slugification
    div {
        classes(ROW, ROW_CARDS, MB_4)
        testId("category-cards")
        monitorsByCategory.forEachIndexed { index, (category, categoryMonitors) ->
            div {
                classes(COL_SM_6, COL_LG_3)
                categoryCard(index, category, categoryMonitors)
            }
        }
    }
    monitorsByCategory.forEachIndexed { index, (category, categoryMonitors) ->
        div {
            id = categoryAnchorId(index)
            classes(CATEGORY_SECTION)
            testId("category-section")
            h2 {
                classes(MB_3)
                +category.label()
            }
            monitorCardGrid(categoryMonitors, historyDays)
        }
    }
}

/**
 * A compact card of a category, showing its aggregated status and linking to the section of its monitors. The
 * card is tinted by a gradient of its status, and closes with the distribution of the statuses it contains.
 */
private fun FlowContent.categoryCard(
    index: Int,
    category: CategoryStatusDto,
    categoryMonitors: List<StatusPageMonitorDetailsDto>,
) {
    a(href = "#${categoryAnchorId(index)}") {
        classes(CARD, CARD_SM, CARD_LINK, CARD_LINK_POP, CARD_GRADIENT, category.status.cardGradient())
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
            statusDistributionBar(categoryMonitors)
        }
    }
}

/**
 * A multi value progress bar of how the statuses are distributed among [monitors], with a tooltip spelling out
 * the exact numbers. Every monitor lands in exactly one segment.
 */
private fun FlowContent.statusDistributionBar(monitors: List<StatusPageMonitorDetailsDto>) {
    val total = monitors.size.takeIf { it > 0 } ?: return

    // Maintenance wins over the uptime status, so a segment always has the color of the `card-status-start`
    // stripe of the monitor's own card in the section below, see `cardStatusClass`
    val maintenanceCnt = monitors.count { it.inMaintenance }
    val upCnt = monitors.count { !it.inMaintenance && it.uptimeStatus == UptimeStatus.UP }
    val downCnt = monitors.count { !it.inMaintenance && it.uptimeStatus == UptimeStatus.DOWN }
    // Whatever is left has no uptime status yet, the enum itself only knows UP and DOWN
    val pendingCnt = total - maintenanceCnt - upCnt - downCnt

    val segments = listOf(
        Triple(upCnt, BG_SUCCESS, Messages.statusPageCategoryUpCount),
        Triple(maintenanceCnt, BG_SECONDARY, Messages.statusPageCategoryMaintenanceCount),
        Triple(pendingCnt, BG_WARNING, Messages.statusPageCategoryPendingCount),
        Triple(downCnt, BG_DANGER, Messages.statusPageCategoryDownCount),
    ).filter { (count, _, _) -> count > 0 }

    div {
        classes(PROGRESS, PROGRESS_SM, MT_3)
        testId("category-distribution")
        tooltip(segments.joinToString(" · ") { (count, _, label) -> label(count) })
        segments.forEach { (count, color, label) ->
            div {
                classes(PROGRESS_BAR, color)
                attributes["role"] = "progressbar"
                ariaLabel(label(count))
                style = "width: ${(count.toDouble() / total).formatAsPercentage()}"
            }
        }
    }
}

private fun categoryAnchorId(index: Int) = "category-$index"

private fun CategoryStatusDto.label(): String = category ?: Messages.statusPageOtherCategory()

private fun FlowContent.monitorCardGrid(monitors: List<StatusPageMonitorDetailsDto>, historyDays: Int) {
    div {
        classes(CSSClass.ROW)
        // DOWN monitors are more important, sort by status (D < U), then by name, ignoring its casing
        monitors
            .sortedWith(compareBy({ it.uptimeStatus?.name.toString() }, { it.name.lowercase() }))
            .forEach { monitor ->
                div {
                    classes(COL_12, MB_3)
                    monitorCard(monitor, historyDays)
                }
            }
    }
}

private fun FlowContent.monitorCard(monitor: StatusPageMonitorDetailsDto, historyDays: Int) {
    div {
        classes(CARD)
        testId("status-monitor-card")
        // Card status indicator
        div {
            classes(CARD_STATUS_START, monitor.uptimeStatus.cardStatusClass(monitor.inMaintenance))
        }
        div {
            classes(CARD_BODY)
            // The name on the left, the uptime ratio and the status on the right
            div {
                classes(D_FLEX, ALIGN_ITEMS_CENTER, GAP_2, MB_2)
                h3 {
                    classes(MB_0)
                    +monitor.name
                }
                div {
                    classes(MS_AUTO, D_FLEX, ALIGN_ITEMS_CENTER, GAP_2)
                    span {
                        classes(TEXT_SECONDARY)
                        testId("monitor-uptime-ratio")
                        tooltip(Messages.statusPageUptimeOfTheLastDays(historyDays.toString()))
                        +(monitor.uptimeRatio?.formatAsPercentage() ?: Messages.noData())
                    }
                    uptimeIndicatorOfStatus(monitor.uptimeStatus, monitor.inMaintenance)
                }
            }
            // The incidents, the last check and the response time
            div {
                classes(D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, GAP_2, MB_3)
                testId("monitor-badges")
                monitorIncidentsBadge(monitor, historyDays)
                lastCheckBadge(monitor)
                if (monitor is WithLatency) {
                    monitor.averageLatencyInMs?.let { avgLatency ->
                        inlineStatusBadge(
                            text = Messages.statusPageResponseTimeValue(avgLatency.toString()),
                            icon = Icon.WAVE_SQUARE,
                            tooltip = Messages.statusPageAverageResponseTime(),
                        )
                    }
                }
            }
            // Tracking blocks
            div {
                classes(TRACKING)
                monitor.uptimeStatusHistory.forEach { trackingBlock(it) }
            }
            monitorIncidentsTemplate(monitor, historyDays)
        }
    }
}

// The time of the last check, or of the last heartbeat of a push monitor
private fun FlowContent.lastCheckBadge(monitor: StatusPageMonitorDetailsDto) {
    val (label, time) = when (monitor) {
        is StatusPagePushMonitorDetailsDto -> Messages.lastHeartbeat() to monitor.lastHeartbeat
        is StatusPageHttpMonitorDetailsDto,
        is StatusPageIcmpMonitorDetailsDto,
        is StatusPageTcpMonitorDetailsDto,
        is StatusPageDnsMonitorDetailsDto,
        is StatusPageDockerMonitorDetailsDto -> Messages.lastCheck() to monitor.lastCheck
    }
    span {
        testId("monitor-last-check")
        inlineStatusBadge(text = time?.timeAgo() ?: Messages.noData(), icon = Icon.CLOCK, tooltip = label)
    }
}

private fun FlowContent.trackingBlock(statusHistoryDto: StatusHistoryDto) {
    div {
        val outageCnt = statusHistoryDto.outageCnt
        val colorClass = when {
            outageCnt == null -> TEXT_MUTED
            outageCnt == 0 -> BG_SUCCESS
            else -> BG_DANGER
        }
        classes(TRACKING_BLOCK, colorClass)
        testId("tracking-block")
        tooltip(statusHistoryDto.tooltipHtml(), html = true)
    }
}

/**
 * The part of an incident that falls on the given day, with full timestamps, so their time zone (the system's one) is
 * explicit, e.g. "2026/10/09 10:00:00 +0200 - 2026/10/09 10:30:00 +0200". An incident spanning multiple days is
 * clipped to the day: it's shown from the start of the day on the days after its start, and until the end of the day
 * (the start of the next one) on the days before its end. Only today has an "ongoing" end.
 */
internal fun StatusPageIncidentDto.spanOnDay(day: LocalDate): String {
    val zone = ZoneId.systemDefault()
    val startOfDay = day.atStartOfDay(zone).toOffsetDateTime()
    val endOfDay = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
    val start = if (startedAt.isBefore(startOfDay)) startOfDay else startedAt.toSystemZone()
    val end = endedAt?.let { resolvedAt -> if (resolvedAt.isAfter(endOfDay)) endOfDay else resolvedAt.toSystemZone() }
        ?: endOfDay.takeUnless { it.isAfter(OffsetDateTime.now()) }
    val endText = end?.toDateTimeStringWithZone() ?: Messages.statusPageIncidentOngoing()

    return "${start.toDateTimeStringWithZone()} - $endText"
}

private fun StatusHistoryDto.tooltipHtml(): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).div {
        classes(TEXT_START)
        div {
            classes(FW_BOLD)
            +date.toDateTimeString()
        }
        div {
            val downtime = downtimeSeconds
            +when (downtime) {
                null -> Messages.noData()
                0L -> Messages.statusPageDayNoDowntime()
                else -> Messages.statusPageDayDowntime(downtime.formatAsInterval())
            }
        }
        if (incidents.isNotEmpty()) {
            ul {
                classes(LIST_UNSTYLED, MB_0, MT_1)
                incidents.forEach { incident ->
                    li {
                        +incident.spanOnDay(date)
                    }
                }
            }
        }
    }
