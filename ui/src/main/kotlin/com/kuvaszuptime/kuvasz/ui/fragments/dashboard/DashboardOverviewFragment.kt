package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.incident.numericMonitorId
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.Duration

internal const val DASHBOARD_STATUS_ID = "dashboard-status"

fun renderDashboardOverview(overview: DashboardOverview): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).div {
        dashboardStatus(overview.uptimeStats, swapOob = true)

        if (overview.uptimeStats.actual.total == 0) {
            emptyState(
                icon = Icon.HEART_RATE_MONITOR,
                title = Messages.noMonitorsYet(),
                subtitle = Messages.noMonitorsYetDescription(),
            )
            return@div
        }
        div {
            classes(ROW, ROW_CARDS)
            keyMetricCards(overview)
            div {
                classes(COL_LG_8)
                monitorTypesCard(overview.uptimeStats)
            }
            div {
                classes(COL_LG_4)
                incidentsCard(overview)
            }
            if (overview.uptimeStats.sslStats.checkedMonitors > 0) {
                div {
                    classes(COL_MD_6, COL_LG_4)
                    certificatesCard(overview.uptimeStats)
                }
            }
            div {
                classes(COL_MD_6, COL_LG_4)
                maintenanceCard(overview.maintenanceWindows, overview.moreMaintenanceWindows)
            }
            div {
                classes(COL_MD_6, COL_LG_4)
                leastReliableMonitorsCard(overview)
            }
        }
    }

/**
 * The verdict of the dashboard in the page header: an indicator with a one-liner about the most important issue, and
 * the same figures below it every time. Without [stats] (before the first load) only the page title is rendered.
 */
internal fun FlowContent.dashboardStatus(stats: DashboardUptimeStats?, swapOob: Boolean = false) {
    val verdict = stats?.verdict()
    div {
        id = DASHBOARD_STATUS_ID
        if (swapOob) hx { swapOob() }
        classes(ROW, G_3, ALIGN_ITEMS_CENTER)
        verdict?.let { (title, statusColor) ->
            div {
                classes(COL_AUTO)
                span {
                    classes(STATUS_INDICATOR, statusColor, STATUS_INDICATOR_ANIMATED)
                    testId("dashboard-status-indicator")
                    ariaLabel(title)
                    repeat(times = 3) {
                        span { classes(STATUS_INDICATOR_CIRCLE) }
                    }
                }
            }
        }
        div {
            classes(CSSClass.COL)
            div {
                classes(PAGE_PRETITLE)
                +Messages.dashboard()
            }
            h2 {
                classes(PAGE_TITLE)
                testId("dashboard-title")
                +(verdict?.title ?: Messages.monitoring())
            }
            if (verdict != null) {
                div {
                    classes(D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, GAP_3, TEXT_SECONDARY)
                    testId("dashboard-status-details")
                    monitorCountFigures(stats.actual, stats.outsideMaintenance, withZeros = true)
                    val lastIncident = stats.actual.lastIncident?.timeAgo()
                    iconCount(
                        Icon.FLAME,
                        text = lastIncident?.let { Messages.lastIncidentAgo(it) } ?: Messages.noIncidents(),
                        value = lastIncident ?: "-",
                    )
                }
            }
        }
    }
}

// The monitors under maintenance are only counted by the maintenance, so every monitor is counted once
private fun DashboardUptimeStats.verdict(): Verdict? = when {
    actual.total == 0 -> null
    // Only the ones outside a maintenance are alarming, and the paused monitors aren't checked at all
    outsideMaintenance.down > 0 ->
        Verdict(Messages.dashboardStatusDown(outsideMaintenance.down, actual.total - actual.paused), STATUS_RED)

    sslStats.invalid > 0 -> Verdict(Messages.dashboardStatusInvalidCertificates(sslStats.invalid), STATUS_RED)
    sslStats.willExpire > 0 -> Verdict(Messages.dashboardStatusExpiringCertificates(), STATUS_YELLOW)
    inMaintenance.down > 0 -> Verdict(Messages.dashboardStatusDownInMaintenance(inMaintenance.down), STATUS_GRAY)
    outsideMaintenance.up > 0 -> Verdict(Messages.dashboardStatusOperational(), STATUS_GREEN)
    outsideMaintenance.pending > 0 -> Verdict(Messages.dashboardStatusPending(), STATUS_YELLOW)
    actual.inMaintenance > 0 -> Verdict(Messages.dashboardMaintenanceCount(actual.inMaintenance), STATUS_GRAY)
    else -> Verdict(Messages.dashboardStatusPaused(), STATUS_CYAN)
}

private data class Verdict(val title: String, val color: CSSClass)

private val SslStats.checkedMonitors: Int
    get() = valid + invalid + willExpire + inProgress

private fun FlowContent.keyMetricCards(overview: DashboardOverview) {
    val history = overview.uptimeStats.history
    val incidents = overview.uptimeStats.incidents
    val timeline = overview.uptimeStats.timeline

    keyMetricCard(
        testId = "dashboard-uptime",
        title = Messages.dashboardUptime(),
        value = history.uptimeRatio?.formatAsPercentage() ?: Messages.noData(),
    ) {
        metricAvatar(Icon.PERCENTAGE, history.uptimeRatio.uptimeColor())
    }
    keyMetricCard(
        testId = "dashboard-incidents-count",
        title = Messages.incidents(),
        value = history.incidents.toString(),
        details = {
            // The resolved ones are counted by the MTTR card already
            if (incidents.ongoingOutsideMaintenance > 0) {
                span {
                    classes(TEXT_RED)
                    +Messages.dashboardOngoingCount(incidents.ongoingOutsideMaintenance)
                }
                +" · "
            }
            if (incidents.ongoingInMaintenance > 0) {
                span { +Messages.dashboardMaintenanceCount(incidents.ongoingInMaintenance) }
                +" · "
            }
            if (history.affectedMonitors > 0) {
                +Messages.dashboardAffectedMonitors(history.affectedMonitors)
            }
        },
    ) {
        timelineSparkline(timeline.map { it.incidents.toLong() }, TEXT_RED, Messages.incidents())
            ?: metricAvatar(Icon.FLAME, BG_SECONDARY_LT)
    }
    keyMetricCard(
        testId = "dashboard-downtime",
        title = Messages.totalDowntime(),
        // Without any incident there is no downtime to measure, but a just started one can be shorter than a second
        value = history.totalDowntimeSeconds.takeIf { history.incidents > 0 }.formatAsIntervalOrDash(),
    ) {
        timelineSparkline(timeline.map { it.downtimeSeconds }, TEXT_ORANGE, Messages.totalDowntime())
            ?: metricAvatar(Icon.CLOCK_DOWN, BG_SECONDARY_LT)
    }
    keyMetricCard(
        testId = "dashboard-mttr",
        title = Messages.dashboardMeanTimeToResolve(),
        value = incidents.meanTimeToResolveSeconds.formatAsIntervalOrDash(),
        details = {
            if (incidents.resolved > 0) {
                +Messages.dashboardResolvedCount(incidents.resolved)
            }
        },
    ) {
        metricAvatar(Icon.TIMER, BG_SECONDARY_LT)
    }
}

/** A dash if there is nothing to measure, otherwise the interval, even if it's shorter than a second. */
private fun Long?.formatAsIntervalOrDash(): String =
    this?.let { seconds -> seconds.takeIf { it > 0 }?.formatAsInterval() ?: Messages.lessThanASecond() } ?: "-"

private fun FlowContent.keyMetricCard(
    testId: String,
    title: String,
    value: String,
    details: FlowContent.() -> Unit = {},
    visual: FlowContent.() -> Unit,
) {
    div {
        classes(COL_SM_6, COL_LG_3)
        div {
            classes(CARD, H_100)
            testId(testId)
            div {
                classes(CARD_BODY)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER)
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(SUBHEADER, MB_1)
                            +title
                        }
                        div {
                            classes(H3, M_0)
                            +value
                        }
                    }
                    div {
                        classes(COL_AUTO)
                        visual()
                    }
                }
                div {
                    classes(TEXT_SECONDARY)
                    details()
                }
            }
        }
    }
}

private fun FlowContent.metricAvatar(icon: Icon, color: CSSClass) {
    span {
        classes(AVATAR, color)
        icon(icon)
    }
}

/** A Tabler bar sparkline of the given values, rendered only if there is anything to show at all. */
private fun FlowContent.timelineSparkline(values: List<Long>, color: CSSClass, label: String): Unit? =
    values.takeIf { it.any { value -> value > 0 } }?.let {
        span {
            classes(SPARKLINE, color)
            dataBsToggle("sparkline")
            attributes["data-bs-type"] = "bar"
            attributes["data-bs-min"] = "0"
            attributes["data-bs-values"] = values.joinToString(",")
            ariaLabel(label)
        }
    }

private fun Double?.uptimeColor(): CSSClass = when {
    this == null -> BG_SECONDARY_LT
    this >= 1.0 -> BG_GREEN_LT
    else -> BG_RED_LT
}

private fun FlowContent.monitorTypesCard(stats: DashboardUptimeStats) {
    div {
        classes(CARD, H_100)
        testId("dashboard-monitor-types")
        div {
            classes(CARD_HEADER)
            cardTitle(Icon.BINOCULARS, Messages.monitors())
        }
        div {
            classes(LIST_GROUP, LIST_GROUP_FLUSH)
            stats.byType.forEach { monitorTypeRow(it) }
        }
    }
}

private fun FlowContent.monitorTypeRow(typeStats: MonitorTypeUptimeStats) {
    val typeUiConfig = MonitorTypeUiConfig.of(typeStats.type)
    div {
        classes(LIST_GROUP_ITEM)
        testId("dashboard-monitor-type-${typeUiConfig.slug}")
        div {
            classes(D_FLEX, ALIGN_ITEMS_CENTER, MB_2)
            span {
                classes(STATUS, typeUiConfig.color.bgColor, typeUiConfig.color.textColor, ME_2)
                icon(typeUiConfig.icon)
            }
            a(href = typeUiConfig.listPath) {
                classes(FW_MEDIUM, TEXT_RESET, ME_2)
                +typeUiConfig.title
            }
            monitorCounts(typeStats.actual, typeStats.outsideMaintenance)
            div {
                classes(MS_AUTO, TEXT_SECONDARY, TEXT_NOWRAP)
                +(
                    typeStats.history.uptimeRatio
                        ?.let { Messages.dashboardUptimeValue(it.formatAsPercentage()) }
                        ?: Messages.noData()
                    )
            }
        }
        div {
            classes(TRACKING)
            typeStats.timeline.forEach { timelineBlock(it) }
        }
    }
}

private fun FlowContent.monitorCounts(stats: ActualUptimeStats, outsideMaintenance: MonitorStateCounts) {
    span {
        classes(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, GAP_2, TEXT_SECONDARY)
        testId("dashboard-monitor-counts")
        monitorCountFigures(stats, outsideMaintenance, withZeros = false)
    }
}

/**
 * The monitor counts as icons with numbers next to them, the labels are only in the tooltips. The monitors under
 * maintenance are only counted by the maintenance. Without [withZeros], only the monitors that are up are shown even
 * if there is none of them.
 */
private fun FlowContent.monitorCountFigures(
    stats: ActualUptimeStats,
    outsideMaintenance: MonitorStateCounts,
    withZeros: Boolean,
) {
    val down = outsideMaintenance.down
    val pending = outsideMaintenance.pending
    monitorCount(Icon.ARROW_NARROW_UP, Messages.up(), outsideMaintenance.up)
    if (withZeros || down > 0) monitorCount(Icon.ARROW_NARROW_DOWN, Messages.down(), down, TEXT_RED.takeIf { down > 0 })
    if (withZeros || stats.inMaintenance > 0) monitorCount(Icon.TOOL, Messages.maintenance(), stats.inMaintenance)
    if (withZeros || stats.paused > 0) monitorCount(Icon.PAUSE, Messages.paused(), stats.paused)
    if (withZeros || pending > 0) iconCount(Icon.HOURGLASS, Messages.dashboardPendingCount(pending), pending.toString())
}

private fun FlowContent.monitorCount(icon: Icon, label: String, count: Int, color: CSSClass? = null) =
    iconCount(icon, text = "$label: $count", value = count.toString(), color)

/** A figure shown as an icon and a short [value], spelled out in full by its tooltip. */
private fun FlowContent.iconCount(icon: Icon, text: String, value: String, color: CSSClass? = null) {
    span {
        classes(setOfNotNull(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, GAP_1, ICON_INLINE, color))
        tooltip(text)
        ariaLabel(text)
        icon(icon)
        +value
    }
}

/** The icon of a monitor or an incident type in the color of the type, named by its tooltip only. */
private fun FlowContent.typeIcon(icon: Icon, color: Color, label: String) {
    span {
        classes(D_INLINE_FLEX, ICON_INLINE, color.textColor)
        testId("dashboard-type-icon")
        tooltip(label)
        ariaLabel(label)
        icon(icon)
    }
}

/** The details of a row, with the same dots between their sections as the text-only details of the other rows. */
private fun FlowContent.detailSections(sections: List<FlowContent.() -> Unit>) {
    div {
        classes(D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, GAP_1)
        sections.forEachIndexed { index, section ->
            if (index > 0) span { +"·" }
            section()
        }
    }
}

private fun textSection(text: String): FlowContent.() -> Unit = { span { +text } }

private fun FlowContent.timelineBlock(slot: UptimeTimelineSlot) {
    // Red even if an incident started too late in the slot to add a whole second of downtime yet
    val isDown = slot.downtimeSeconds > 0 || slot.incidents > 0
    val hasData = isDown || slot.uptimeSeconds > 0
    div {
        classes(
            TRACKING_BLOCK,
            when {
                isDown -> BG_DANGER
                hasData -> BG_SUCCESS
                else -> TEXT_MUTED
            }
        )
        tooltip(
            listOfNotNull(
                Messages.dashboardTimelineSlot(slot.start.toDateTimeString(), slot.end.toDateTimeString()),
                Messages.noData().takeUnless { hasData },
                slot.incidents.takeIf { it > 0 }?.let { "${Messages.incidents()}: $it" },
            ).joinToString(" · ")
        )
    }
}

private fun FlowContent.incidentsCard(overview: DashboardOverview) {
    val incidents = overview.recentIncidents
    div {
        classes(CARD, H_100)
        testId("dashboard-recent-incidents")
        div {
            classes(CARD_HEADER)
            cardTitle(
                Icon.FLAME,
                Messages.dashboardRecentIncidents(),
                subtitle = overview.period.lastPeriodLabel(),
            )
        }
        if (incidents.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoIncidents()
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                incidents.forEach { incident ->
                    incidentRow(
                        incident,
                        inMaintenance = incident.numericMonitorId in overview.uptimeStats.monitorsInMaintenance,
                    )
                }
                if (overview.moreOngoingIncidents > 0) {
                    moreItemsRow(
                        href = "/incidents?period=${overview.period}",
                        testId = "dashboard-more-ongoing-incidents",
                        text = Messages.dashboardMoreOngoingIncidents(overview.moreOngoingIncidents),
                    )
                }
            }
        }
        div {
            classes(CARD_FOOTER, MT_AUTO)
            a(href = "/incidents?period=${overview.period}") {
                +Messages.dashboardViewAllIncidents()
            }
        }
    }
}

private fun FlowContent.incidentRow(incident: IncidentDto, inMaintenance: Boolean) {
    val isOngoing = incident.status == IncidentStatus.ONGOING
    val details = if (isOngoing) {
        listOf(Messages.dashboardIncidentStarted(incident.startedAt.timeAgo()))
    } else {
        listOfNotNull(
            getDurationOfEvent(
                isMonitorEnabled = incident.isMonitorEnabled,
                startedAt = incident.startedAt,
                endedAt = incident.endedAt,
                updatedAt = incident.updatedAt,
            ).formatAsIntervalOrDash(),
            incident.endedAt?.let { Messages.dashboardIncidentResolvedAgo(it.timeAgo()) },
        )
    }
    val (dotClasses, state) = when {
        isOngoing && inMaintenance -> setOf(STATUS_GRAY) to Messages.maintenance()
        isOngoing -> setOf(STATUS_RED, STATUS_DOT_ANIMATED) to Messages.dashboardIncidentOngoing()
        else -> setOf(STATUS_GREEN) to Messages.dashboardIncidentResolved()
    }
    listGroupRow(
        testId = "dashboard-incident",
        dotClasses = dotClasses,
        dotTooltip = state,
        title = incident.monitorName,
        href = incident.getMonitorUrl(),
        titleTooltip = incident.details,
    ) {
        val type = incident.incidentType
        detailSections(
            listOf<FlowContent.() -> Unit> { typeIcon(type.icon, type.color, type.label) } +
                details.map(::textSection)
        )
    }
}

private fun FlowContent.certificatesCard(stats: DashboardUptimeStats) {
    div {
        classes(CARD, H_100)
        testId("dashboard-certificates")
        div {
            classes(CARD_HEADER)
            cardTitle(Icon.LOCK_CLOSED, Messages.dashboardCertificates())
            div {
                classes(CARD_ACTIONS)
                span {
                    classes(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, GAP_2, TEXT_SECONDARY)
                    testId("dashboard-certificate-counts")
                    monitorCount(Icon.LOCK_CLOSED, Messages.valid(), stats.sslStats.valid)
                    if (stats.sslStats.willExpire > 0) {
                        monitorCount(Icon.TIMER, Messages.expiresSoon(), stats.sslStats.willExpire, TEXT_YELLOW)
                    }
                    if (stats.sslStats.invalid > 0) {
                        monitorCount(Icon.LOCK_OPEN, Messages.invalid(), stats.sslStats.invalid, TEXT_RED)
                    }
                    stats.sslStats.inProgress.takeIf { it > 0 }?.let { pending ->
                        iconCount(Icon.HOURGLASS, Messages.dashboardPendingCount(pending), pending.toString())
                    }
                }
            }
        }
        if (stats.certificatesWithIssues.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                // Nothing is claimed about the certificates that haven't been checked yet
                +when {
                    stats.sslStats.valid == 0 -> Messages.dashboardCertificatesPending()
                    stats.sslStats.inProgress > 0 -> Messages.dashboardAllCheckedCertificatesValid()
                    else -> Messages.dashboardAllCertificatesValid()
                }
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                stats.certificatesWithIssues.forEach { certificateRow(it) }
                val moreCertificatesWithIssues =
                    stats.sslStats.invalid + stats.sslStats.willExpire - stats.certificatesWithIssues.size
                if (moreCertificatesWithIssues > 0) {
                    moreItemsRow(
                        href = MonitorTypeUiConfig.HTTP.listPath,
                        testId = "dashboard-more-certificates",
                        text = Messages.dashboardMoreCertificatesWithIssues(moreCertificatesWithIssues),
                    )
                }
            }
        }
    }
}

private fun FlowContent.certificateRow(monitor: HttpMonitorSummary) {
    val isInvalid = monitor.sslStatus == SslStatus.INVALID
    listGroupRow(
        testId = "dashboard-certificate",
        dotClasses = setOf(if (isInvalid) STATUS_RED else STATUS_YELLOW),
        dotTooltip = if (isInvalid) Messages.invalid() else Messages.expiresSoon(),
        title = monitor.name,
        href = sslEventsPath(monitor.id),
        details = if (isInvalid) {
            monitor.sslError ?: Messages.invalid()
        } else {
            monitor.sslValidUntil?.let { Messages.dashboardCertificateExpires(it.timeAgo()) } ?: Messages.expiresSoon()
        },
    )
}

private fun FlowContent.maintenanceCard(windows: List<MaintenanceWindowDetailsDto>, moreWindows: Int) {
    div {
        classes(CARD, H_100)
        testId("dashboard-maintenance")
        div {
            classes(CARD_HEADER)
            cardTitle(Icon.TOOL, Messages.maintenance(), subtitle = Messages.dashboardNext7Days())
        }
        if (windows.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoMaintenanceInNext7Days()
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                windows.forEach { maintenanceWindowRow(it) }
                if (moreWindows > 0) {
                    moreItemsRow(
                        href = "/maintenance-windows",
                        testId = "dashboard-more-maintenance-windows",
                        text = Messages.dashboardMoreMaintenanceWindows(moreWindows),
                    )
                }
            }
        }
        div {
            classes(CARD_FOOTER, MT_AUTO)
            a(href = "/maintenance-windows") {
                +Messages.dashboardManageMaintenanceWindows()
            }
        }
    }
}

/** The title of a card of the dashboard, with the same icon as the related page has in the navigation. */
private fun FlowContent.cardTitle(icon: Icon, title: String, subtitle: String? = null) {
    h3 {
        classes(CARD_TITLE, D_FLEX, ALIGN_ITEMS_CENTER, GAP_2)
        span {
            classes(D_INLINE_FLEX, TEXT_SECONDARY)
            icon(icon)
        }
        +title
        subtitle?.let { text ->
            span {
                classes(CARD_SUBTITLE)
                testId("dashboard-card-subtitle")
                +text
            }
        }
    }
}

private fun FlowContent.maintenanceWindowRow(window: MaintenanceWindowDetailsDto) {
    listGroupRow(
        testId = "dashboard-maintenance-window",
        dotClasses = if (window.active) setOf(STATUS_GREEN, STATUS_DOT_ANIMATED) else setOf(STATUS_YELLOW),
        dotTooltip = if (window.active) {
            Messages.dashboardMaintenanceActive()
        } else {
            Messages.dashboardMaintenanceScheduled()
        },
        title = window.name,
        href = "/maintenance-windows/${window.id}",
        details = if (window.active) {
            window.endsAt
                ?.let { Messages.dashboardMaintenanceEnds(it.timeAgo()) }
                ?: Messages.dashboardMaintenanceUntilFurtherNotice()
        } else {
            window.nextStart?.let { Messages.dashboardMaintenanceStarts(it.timeAgo()) }.orEmpty()
        },
    )
}

private fun FlowContent.leastReliableMonitorsCard(overview: DashboardOverview) {
    val monitors = overview.uptimeStats.leastReliableMonitors
    div {
        classes(CARD, H_100)
        testId("dashboard-least-reliable-monitors")
        div {
            classes(CARD_HEADER)
            cardTitle(
                Icon.HEART_BROKEN,
                Messages.dashboardLeastReliableMonitors(),
                subtitle = overview.period.lastPeriodLabel(),
            )
        }
        if (monitors.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoDownMonitors()
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                monitors.forEach { unreliableMonitorRow(it) }
            }
        }
    }
}

private fun FlowContent.unreliableMonitorRow(monitor: UnreliableMonitor) {
    val (dotClasses, state) = when {
        monitor.inMaintenance -> setOf(STATUS_GRAY) to Messages.maintenance()
        monitor.uptimeStatus == UptimeStatus.DOWN -> setOf(STATUS_RED, STATUS_DOT_ANIMATED) to Messages.down()
        monitor.uptimeStatus == UptimeStatus.UP -> setOf(STATUS_GREEN) to Messages.up()
        else -> setOf(STATUS_YELLOW) to Messages.inProgress()
    }
    val typeUiConfig = MonitorTypeUiConfig.of(monitor.id.type)
    listGroupRow(
        testId = "dashboard-least-reliable-monitor",
        dotClasses = dotClasses,
        dotTooltip = state,
        title = monitor.name,
        href = typeUiConfig.detailsPath(monitor.id.id),
    ) {
        val uptime = monitor.history.uptimeRatio?.formatAsPercentage()
        val uptimeText = uptime?.let { Messages.dashboardUptimeValue(it) } ?: Messages.noData()
        val incidents = monitor.history.incidents.toString()
        val downtime = monitor.history.totalDowntimeSeconds.formatAsIntervalOrDash()
        detailSections(
            listOf(
                { typeIcon(typeUiConfig.icon, typeUiConfig.color, typeUiConfig.title) },
                { iconCount(Icon.PERCENTAGE, text = uptimeText, value = uptime ?: "-") },
                { iconCount(Icon.FLAME, text = "${Messages.incidents()}: $incidents", value = incidents) },
                { iconCount(Icon.CLOCK_DOWN, text = "${Messages.totalDowntime()}: $downtime", value = downtime) },
            )
        )
    }
}

/** The last row of a capped list, calling out the items that didn't fit, and linking to where all of them are. */
private fun FlowContent.moreItemsRow(href: String, testId: String, text: String) {
    a(href = href) {
        classes(LIST_GROUP_ITEM, LIST_GROUP_ITEM_ACTION)
        testId(testId)
        +text
    }
}

private fun FlowContent.listGroupRow(
    testId: String,
    dotClasses: Set<CSSClass>,
    dotTooltip: String,
    title: String,
    href: String,
    details: String,
    titleTooltip: String? = null,
) = listGroupRow(testId, dotClasses, dotTooltip, title, href, titleTooltip) { +details }

/** A two-line row of a flush list group: a status dot telling the state and a linked title with details below it. */
private fun FlowContent.listGroupRow(
    testId: String,
    dotClasses: Set<CSSClass>,
    dotTooltip: String,
    title: String,
    href: String,
    titleTooltip: String? = null,
    details: FlowContent.() -> Unit,
) {
    div {
        classes(LIST_GROUP_ITEM)
        testId(testId)
        div {
            classes(ROW, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_AUTO)
                span {
                    classes(dotClasses + STATUS_DOT)
                    testId("dashboard-status-dot")
                    tooltip(dotTooltip)
                    ariaLabel(dotTooltip)
                }
            }
            div {
                classes(CSSClass.COL, TEXT_TRUNCATE)
                a(href = href) {
                    classes(D_BLOCK, FW_MEDIUM, TEXT_RESET, TEXT_TRUNCATE)
                    titleTooltip?.let { tooltip(it) }
                    +title
                }
                div {
                    classes(TEXT_SECONDARY, TEXT_WRAP, TEXT_BREAK)
                    details()
                }
            }
        }
    }
}

/** One message for every period of the selector, as the number of the period decides the grammar of the text. */
private fun Duration.lastPeriodLabel(): String = when (this) {
    UIDefaults.LAST_HOUR -> Messages.dashboardLastHour()
    UIDefaults.LAST_6_HOURS -> Messages.dashboardLast6Hours()
    UIDefaults.LAST_12_HOURS -> Messages.dashboardLast12Hours()
    UIDefaults.LAST_24_HOURS -> Messages.dashboardLast24Hours()
    UIDefaults.LAST_7_DAYS -> Messages.dashboardLast7Days()
    UIDefaults.LAST_30_DAYS -> Messages.dashboardLast30Days()
    else -> formatAsSimpleInterval()
}
