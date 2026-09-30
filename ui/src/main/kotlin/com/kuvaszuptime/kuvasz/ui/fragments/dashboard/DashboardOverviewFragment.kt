package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
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
                maintenanceCard(overview.maintenanceWindows, overview.maintenanceLookahead)
            }
            div {
                classes(COL_MD_6, COL_LG_4)
                leastReliableMonitorsCard(overview)
            }
        }
    }

/**
 * The verdict of the dashboard in the page header: an indicator with a one-liner about the most important issue, and
 * the figures worth knowing about below it. Without [stats] (before the first load) only the page title is rendered.
 */
internal fun FlowContent.dashboardStatus(stats: DashboardUptimeStats?, swapOob: Boolean = false) {
    val verdict = stats?.verdict()
    val details = verdict?.let { (title, _) -> stats.statusDetails().filter { it.text != title } }.orEmpty()
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
                +(verdict?.first ?: Messages.monitoring())
            }
            if (details.isNotEmpty()) {
                div {
                    classes(D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, GAP_3, TEXT_SECONDARY)
                    testId("dashboard-status-details")
                    details.forEach { iconCount(it.icon, it.text, it.value, it.color) }
                }
            }
        }
    }
}

private fun DashboardUptimeStats.verdict(): Pair<String, CSSClass>? = when {
    actual.total == 0 -> null
    // The paused monitors aren't checked at all, so they aren't counted
    hasDownMonitorsOutsideMaintenance ->
        Messages.dashboardStatusDown(actual.down, actual.total - actual.paused) to STATUS_RED
    sslStats.invalid > 0 -> Messages.dashboardStatusInvalidCertificates(sslStats.invalid) to STATUS_RED
    sslStats.willExpire > 0 -> Messages.dashboardStatusExpiringCertificates() to STATUS_YELLOW
    // Being down is expected during a maintenance, so it's only worth a neutral mention
    downInMaintenance > 0 -> Messages.dashboardStatusDownInMaintenance(downInMaintenance) to STATUS_GRAY
    // Nothing is down at this point, so without any monitor being up, the rest of them is either pending or paused
    actual.up > 0 -> Messages.dashboardStatusOperational() to STATUS_GREEN
    actual.inProgress > 0 -> Messages.dashboardStatusPending() to STATUS_YELLOW
    else -> Messages.dashboardStatusPaused() to STATUS_CYAN
}

private val DashboardUptimeStats.hasDownMonitorsOutsideMaintenance: Boolean
    get() = actual.down > downInMaintenance

/** A figure of the header's status line: shown as an icon with a [value], and spelled out in full by [text]. */
private data class StatusDetail(val icon: Icon, val text: String, val value: String, val color: CSSClass? = null)

private fun DashboardUptimeStats.statusDetails(): List<StatusDetail> = listOfNotNull(
    StatusDetail(Icon.ARROW_NARROW_UP, "${Messages.up()}: ${actual.up}", actual.up.toString()),
    actual.down.takeIf { it > 0 }?.let { down ->
        StatusDetail(
            Icon.ARROW_NARROW_DOWN,
            "${Messages.down()}: $down",
            down.toString(),
            TEXT_RED.takeIf { hasDownMonitorsOutsideMaintenance },
        )
    },
    actual.inMaintenance.takeIf { it > 0 }?.let {
        StatusDetail(Icon.TOOL, Messages.dashboardMaintenanceCount(it), it.toString())
    },
    actual.paused.takeIf { it > 0 }?.let { StatusDetail(Icon.PAUSE, Messages.dashboardPausedCount(it), it.toString()) },
    actual.inProgress.takeIf { it > 0 }?.let {
        StatusDetail(Icon.HOURGLASS, Messages.dashboardPendingCount(it), it.toString())
    },
    sslStats.invalid.takeIf { it > 0 }?.let {
        StatusDetail(Icon.LOCK_OPEN, Messages.dashboardStatusInvalidCertificates(it), it.toString(), TEXT_RED)
    },
    sslStats.willExpire.takeIf { it > 0 }?.let {
        StatusDetail(Icon.TIMER, Messages.dashboardExpiringCertificateCount(it), it.toString(), TEXT_YELLOW)
    },
    actual.lastIncident?.let { lastIncident ->
        lastIncident.timeAgo().let { StatusDetail(Icon.FLAME, Messages.lastIncidentAgo(it), it) }
    },
)

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
            // The resolved ones are left out, the MTTR card counts them already
            if (incidents.ongoing > 0) {
                span {
                    classes(TEXT_RED)
                    +Messages.dashboardOngoingCount(incidents.ongoing)
                }
                +" · "
            }
            +Messages.dashboardAffectedMonitors(history.affectedMonitors)
        },
    ) {
        timelineSparkline(timeline.map { it.incidents.toLong() }, TEXT_RED, Messages.incidents())
            ?: metricAvatar(Icon.FLAME, BG_SECONDARY_LT)
    }
    keyMetricCard(
        testId = "dashboard-downtime",
        title = Messages.totalDowntime(),
        value = history.totalDowntimeSeconds.formatAsIntervalOrDash(),
    ) {
        timelineSparkline(timeline.map { it.downtimeSeconds }, TEXT_ORANGE, Messages.totalDowntime())
            ?: metricAvatar(Icon.CLOCK_DOWN, BG_SECONDARY_LT)
    }
    keyMetricCard(
        testId = "dashboard-mttr",
        title = Messages.dashboardMeanTimeToResolve(),
        value = incidents.meanTimeToResolveSeconds.formatAsIntervalOrDash(),
        details = { +Messages.dashboardResolvedCount(incidents.resolved) },
    ) {
        metricAvatar(Icon.TIMER, BG_SECONDARY_LT)
    }
}

private fun Long?.formatAsIntervalOrDash(): String = this?.takeIf { it > 0 }?.formatAsInterval() ?: "-"

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
                // Below the value and the visual, so it gets the whole width of the card and never wraps next to them
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

// The same UP/DOWN semantics as everywhere else: any downtime at all is red
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
            monitorCounts(typeStats.actual)
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

/**
 * The monitor counts of a type as icons with numbers next to them, leaving out the zeros except for the monitors that
 * are up. The labels are only in the tooltips, so they don't make the rows noisy.
 */
private fun FlowContent.monitorCounts(stats: ActualUptimeStats) {
    span {
        classes(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, GAP_2, TEXT_SECONDARY)
        testId("dashboard-monitor-counts")
        monitorCount(Icon.ARROW_NARROW_UP, Messages.up(), stats.up)
        if (stats.down > 0) monitorCount(Icon.ARROW_NARROW_DOWN, Messages.down(), stats.down, TEXT_RED)
        if (stats.inMaintenance > 0) monitorCount(Icon.TOOL, Messages.maintenance(), stats.inMaintenance)
        if (stats.paused > 0) monitorCount(Icon.PAUSE, Messages.paused(), stats.paused)
        if (stats.inProgress > 0) {
            iconCount(Icon.HOURGLASS, Messages.dashboardPendingCount(stats.inProgress), stats.inProgress.toString())
        }
    }
}

private fun FlowContent.monitorCount(icon: Icon, label: String, count: Int, color: CSSClass? = null) =
    iconCount(icon, text = "$label: $count", value = count.toString(), color)

/** A figure shown as an icon and a short [value], spelled out in full by its tooltip. */
private fun FlowContent.iconCount(icon: Icon, text: String, value: String, color: CSSClass? = null) {
    span {
        // icon-inline scales the icon down to the size of the text next to it
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
    val uptimeRatio = slot.uptimeRatio
    div {
        classes(
            TRACKING_BLOCK,
            when {
                // The same UP/DOWN semantics as everywhere else: a slot with any downtime at all is red, even if an
                // incident started too late in it to add a whole second of downtime yet
                slot.downtimeSeconds > 0 || slot.incidents > 0 -> BG_DANGER
                uptimeRatio == null -> TEXT_MUTED
                else -> BG_SUCCESS
            }
        )
        tooltip(
            listOfNotNull(
                Messages.dashboardTimelineSlot(slot.start.toDateTimeString(), slot.end.toDateTimeString()),
                uptimeRatio?.let { Messages.dashboardUptimeValue(it.formatAsPercentage()) } ?: Messages.noData(),
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
                subtitle = Messages.dashboardLastX(overview.period.formatAsSimpleInterval()),
            )
        }
        if (incidents.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoIncidents(overview.period.formatAsSimpleInterval())
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                incidents.forEach { incident ->
                    val monitorId = NumericMonitorID(incident.incidentType.monitorType, incident.monitorId)
                    incidentRow(incident, inMaintenance = monitorId in overview.uptimeStats.monitorsInMaintenance)
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
    // The dot alone tells the state of the incident, just like on the incidents page
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
                // The same icons as in the status line of the page header
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
                }
            }
        }
        if (stats.certificatesWithIssues.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardAllCertificatesValid()
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                stats.certificatesWithIssues.forEach { certificateRow(it) }
            }
        }
    }
}

private fun FlowContent.certificateRow(monitor: HttpMonitorDetailsDto) {
    val isInvalid = monitor.sslStatus == SslStatus.INVALID
    listGroupRow(
        testId = "dashboard-certificate",
        dotClasses = setOf(if (isInvalid) STATUS_RED else STATUS_YELLOW),
        dotTooltip = if (isInvalid) Messages.invalid() else Messages.expiresSoon(),
        title = monitor.name,
        href = "${MonitorTypeUiConfig.HTTP.detailsPath(monitor.id)}#http-monitor-details-ssl-events",
        details = if (isInvalid) {
            monitor.sslError ?: Messages.invalid()
        } else {
            monitor.sslValidUntil?.let { Messages.dashboardCertificateExpires(it.timeAgo()) } ?: Messages.expiresSoon()
        },
    )
}

private fun FlowContent.maintenanceCard(windows: List<MaintenanceWindowDetailsDto>, lookaheadPeriod: Duration) {
    val lookahead = lookaheadPeriod.formatAsSimpleInterval()
    div {
        classes(CARD, H_100)
        testId("dashboard-maintenance")
        div {
            classes(CARD_HEADER)
            cardTitle(Icon.TOOL, Messages.maintenance(), subtitle = Messages.dashboardNextX(lookahead))
        }
        if (windows.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoMaintenance(lookahead)
            }
        } else {
            div {
                classes(LIST_GROUP, LIST_GROUP_FLUSH)
                windows.forEach { maintenanceWindowRow(it) }
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
        // Without being a flex container itself, the icon would sit on the baseline, below the middle of the text
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
        // The same colors as on the list of the maintenance windows
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
    val period = overview.period.formatAsSimpleInterval()
    div {
        classes(CARD, H_100)
        testId("dashboard-least-reliable-monitors")
        div {
            classes(CARD_HEADER)
            cardTitle(
                Icon.HEART_BROKEN,
                Messages.dashboardLeastReliableMonitors(),
                subtitle = Messages.dashboardLastX(period),
            )
        }
        if (monitors.isEmpty()) {
            div {
                classes(CARD_BODY, TEXT_SECONDARY)
                +Messages.dashboardNoDownMonitors(period)
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
    // The dot tells the current state of the monitor, the details tell how it did over the period
    val (dotClasses, state) = when {
        !monitor.enabled -> setOf(STATUS_CYAN) to Messages.paused()
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
        // The same icons as the uptime, the incidents and the downtime cards have
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
                // Only the title is truncated, the details wrap to as many lines as they need
                div {
                    classes(TEXT_SECONDARY, TEXT_WRAP, TEXT_BREAK)
                    details()
                }
            }
        }
    }
}
