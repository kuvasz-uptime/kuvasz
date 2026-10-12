package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.label
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.incident.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import kotlinx.html.*
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal const val STATUS_PAGE_INCIDENTS_MODAL_ID = "status-page-incidents-modal"
private const val RECENTLY_RESOLVED_HOURS = 24L

internal typealias IncidentsByMonitor = List<Pair<StatusPageMonitorDetailsDto, List<StatusPageIncidentDto>>>

// Every incident of the monitor's history once (an incident spanning multiple days is listed on each of them), the
// ongoing ones first, then the latest ones
internal val StatusPageMonitorDetailsDto.incidentsOfHistory: List<StatusPageIncidentDto>
    get() = uptimeStatusHistory
        .flatMap { it.incidents }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<StatusPageIncidentDto> { it.isOngoing }.thenByDescending { it.startedAt })

private val StatusPageIncidentDto.isOngoing: Boolean
    get() = endedAt == null

// An ongoing incident of a monitor under maintenance is grayed out, like the card of the monitor itself
internal fun StatusPageMonitorDetailsDto.incidentStatusColor(isOngoing: Boolean): CSSClass = when {
    !isOngoing -> StatusPageAccordion.RESOLVED_INCIDENTS.statusColor
    inMaintenance -> BG_SECONDARY
    else -> StatusPageAccordion.ACTIVE_INCIDENTS.statusColor
}

// Every monitor with the incidents of its history, ordered by the names of the monitors
internal fun StatusPageDataDto.incidentsByMonitor(): IncidentsByMonitor =
    monitors
        .sortedBy { it.name.lowercase() }
        .map { monitor -> monitor to monitor.incidentsOfHistory }

internal fun IncidentsByMonitor.ongoing(): IncidentsByMonitor = filterIncidents { incident -> incident.isOngoing }

internal fun IncidentsByMonitor.recentlyResolved(generatedAt: OffsetDateTime): IncidentsByMonitor {
    val resolvedSince = generatedAt.minusHours(RECENTLY_RESOLVED_HOURS)
    return filterIncidents { incident -> incident.endedAt?.isAfter(resolvedSince) == true }
}

private fun IncidentsByMonitor.filterIncidents(predicate: (StatusPageIncidentDto) -> Boolean): IncidentsByMonitor =
    map { (monitor, incidents) -> monitor to incidents.filter(predicate) }
        .filter { (_, incidents) -> incidents.isNotEmpty() }

internal fun FlowContent.monitorIncidentsCard(
    monitor: StatusPageMonitorDetailsDto,
    incidents: List<StatusPageIncidentDto>,
    isOngoing: Boolean,
) {
    statusCard(monitor.incidentStatusColor(isOngoing), cardTestId = "status-page-monitor-incidents") {
        h4 {
            classes(CARD_TITLE, MB_0)
            +monitor.name
        }
        incidents.forEach { incident ->
            div {
                classes(MT_3)
                publicIncident(incident)
            }
        }
    }
}

/** An incident with its span and its public timeline, as the visitors of the status pages see it. */
private fun FlowContent.publicIncident(incident: StatusPageIncidentDto) {
    div {
        testId("public-incident")
        div {
            classes(TEXT_SECONDARY, MB_2)
            +incident.spanText()
        }
        publicIncidentTimeline(incident)
    }
}

/**
 * The incident's public timeline, the latest event first: its start, its public notes at their own time, and its
 * resolution. The `RESOLVED` notes right before or after the resolution are shown as part of it, see
 * [publicTimelineNodes]. It looks like the timeline of the admin UI, without the details of the incident, its
 * integrations, and the controls of the notes.
 */
private fun FlowContent.publicIncidentTimeline(incident: StatusPageIncidentDto) {
    ul {
        classes(TIMELINE, MB_0)
        testId("public-incident-timeline")
        incident.publicTimelineNodes().forEach { node ->
            when (node) {
                is PublicTimelineNode.Started -> timelineEvent(
                    icon = Icon.FLAME,
                    color = Color.RED_LT,
                    time = node.time,
                    title = Messages.incidentStartedEvent(),
                    eventTestId = "public-incident-started",
                ) {}

                is PublicTimelineNode.Note -> timelineEvent(
                    icon = node.note.status.icon,
                    color = node.note.status.color,
                    time = node.time,
                    title = node.note.status.label,
                    eventTestId = "public-incident-note",
                ) {
                    noteContent(node.note)
                }

                is PublicTimelineNode.Resolved -> timelineEvent(
                    icon = Icon.CIRCLE_CHECK_FILLED,
                    color = Color.GREEN_LT,
                    time = node.time,
                    title = Messages.incidentResolvedEvent(),
                    eventTestId = "public-incident-resolved",
                ) {
                    node.notes.forEach { note -> noteContent(note) }
                }
            }
        }
    }
}

private fun FlowContent.noteContent(note: StatusPageIncidentNoteDto) {
    p {
        classes(TEXT_PRE_LINE, TEXT_BREAK, MB_0, MT_1)
        testId("public-incident-note-content")
        +note.content
    }
}

internal sealed interface PublicTimelineNode {
    val time: OffsetDateTime

    data class Started(override val time: OffsetDateTime) : PublicTimelineNode

    data class Note(val note: StatusPageIncidentNoteDto) : PublicTimelineNode {
        override val time: OffsetDateTime = note.timestamp
    }

    data class Resolved(
        override val time: OffsetDateTime,
        val notes: List<StatusPageIncidentNoteDto>,
    ) : PublicTimelineNode
}

/**
 * The nodes of the public timeline, the latest one first. Every node is placed by its time, but the `RESOLVED` notes
 * that are the direct neighbours of the resolution (right before or after it, without any other note in between) are
 * shown as part of the resolution. At the very same time the start and the resolution come before the notes.
 */
internal fun StatusPageIncidentDto.publicTimelineNodes(): List<PublicTimelineNode> {
    val endedAt = endedAt
    val chronological = buildList {
        add(PublicTimelineNode.Started(startedAt))
        endedAt?.let { add(PublicTimelineNode.Resolved(it, emptyList())) }
        notes.forEach { add(PublicTimelineNode.Note(it)) }
    }.sortedWith(compareBy<PublicTimelineNode> { it.time.toInstant() }.thenBy { it is PublicTimelineNode.Note })
    if (endedAt == null) return chronological.reversed()

    val resolvedIndex = chronological.indexOfFirst { it is PublicTimelineNode.Resolved }
    val absorbedBefore = chronological.subList(0, resolvedIndex).takeLastWhile { it.isResolvedNote() }
    val absorbedAfter = chronological.subList(resolvedIndex + 1, chronological.size).takeWhile { it.isResolvedNote() }
    val absorbed = (absorbedBefore + absorbedAfter).map { (it as PublicTimelineNode.Note).note }

    return (
        chronological.subList(0, resolvedIndex - absorbedBefore.size) +
            PublicTimelineNode.Resolved(endedAt, absorbed) +
            chronological.subList(resolvedIndex + 1 + absorbedAfter.size, chronological.size)
        ).reversed()
}

private fun PublicTimelineNode.isResolvedNote() =
    this is PublicTimelineNode.Note && note.status == InvestigationStatus.RESOLVED

/**
 * The badge of a monitor card counting the incidents of its history, which opens the incidents modal, filled from the
 * template of the monitor, see [monitorIncidentsTemplate]. Nothing is rendered without incidents.
 */
internal fun FlowContent.monitorIncidentsBadge(
    monitor: StatusPageMonitorDetailsDto,
    incidents: List<StatusPageIncidentDto>,
    historyDays: Int,
) {
    if (incidents.isEmpty()) return
    a(href = "#") {
        classes(TEXT_RESET)
        testId("monitor-incidents-badge")
        modalOpener(STATUS_PAGE_INCIDENTS_MODAL_ID)
        attributes["data-incidents-of"] = monitor.incidentsKey
        onClick = "showStatusPageIncidents(this.dataset.incidentsOf)"
        inlineStatusBadge(
            text = incidents.size.toString(),
            icon = Icon.FLAME,
            tooltip = Messages.statusPageIncidentsInTheLastDays(historyDays.toString()),
        )
    }
}

/**
 * The content of the incidents modal for the given monitor. It's kept in the monitor's card, so it's refreshed
 * together with the page, and copied into the modal (which is outside the refreshed part of the page, so it stays
 * open) when it's opened, and after every refresh while it's open.
 */
internal fun FlowContent.monitorIncidentsTemplate(
    monitor: StatusPageMonitorDetailsDto,
    incidents: List<StatusPageIncidentDto>,
    historyDays: Int,
) {
    if (incidents.isEmpty()) return
    templateTag {
        attributes["data-incidents-of"] = monitor.incidentsKey
        div {
            classes(MODAL_HEADER)
            h5 {
                classes(MODAL_TITLE)
                +Messages.statusPageIncidentsOfMonitor(monitor.name, historyDays.toString())
            }
            button(type = ButtonType.button) {
                classes(BTN_CLOSE)
                modalCloser()
                ariaLabel(Messages.close())
            }
        }
        div {
            classes(MODAL_BODY, D_FLEX, FLEX_COLUMN, GAP_3)
            testId("status-page-incidents-modal-body")
            incidents.forEach { incident ->
                statusCard(
                    statusColor = monitor.incidentStatusColor(incident.isOngoing),
                    cardTestId = "status-page-incident-card",
                ) {
                    publicIncident(incident)
                }
            }
        }
    }
}

/** The shell of the incidents modal, outside the refreshed part of the page, see [monitorIncidentsTemplate]. */
internal fun FlowContent.statusPageIncidentsModal() {
    div {
        id = STATUS_PAGE_INCIDENTS_MODAL_ID
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        testId("status-page-incidents-modal")
        tabIndex = "-1"
        role = "dialog"
        div {
            classes(MODAL_DIALOG, MODAL_DIALOG_CENTERED, MODAL_DIALOG_SCROLLABLE, MODAL_LG)
            role = "document"
            div {
                classes(MODAL_CONTENT)
            }
        }
    }
}

// The type and the name identify a monitor of a page, and they are matched as plain strings, never as selectors
private val StatusPageMonitorDetailsDto.incidentsKey: String
    get() = "$type:$name"

/**
 * The time span of an incident, e.g. "2026/10/09 05:21:07 - 07:30:12 (2 hours 9 minutes)", or with "ongoing" as its
 * end.
 */
internal fun StatusPageIncidentDto.spanText(): String {
    val start = startedAt.toSystemZone().toDateTimeString()
    val end = endedAt?.let { endedAt ->
        if (endedAt.toSystemZone().toLocalDate() == startedAt.toSystemZone().toLocalDate()) {
            endedAt.toSystemZone().format(TIME_FORMATTER)
        } else {
            endedAt.toSystemZone().toDateTimeString()
        }
    }
    return if (end == null) {
        "$start - ${Messages.statusPageIncidentOngoing()}"
    } else {
        "$start - $end (${Duration.between(startedAt, endedAt).seconds.formatAsInterval()})"
    }
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
