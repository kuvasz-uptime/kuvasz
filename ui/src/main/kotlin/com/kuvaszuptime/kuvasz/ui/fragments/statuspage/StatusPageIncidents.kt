package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val STATUS_PAGE_INCIDENTS_MODAL_ID = "status-page-incidents-modal"
private const val RECENTLY_RESOLVED_HOURS = 24L

// Every incident of the monitor's history once (an incident spanning multiple days is listed on each of them), the
// ongoing ones first, then the latest ones
internal val StatusPageMonitorDetailsDto.incidentsOfHistory: List<StatusPageIncidentDto>
    get() = uptimeStatusHistory
        .flatMap { it.incidents }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<StatusPageIncidentDto> { it.isOngoing }.thenByDescending { it.startedAt })

private val StatusPageIncidentDto.isOngoing: Boolean
    get() = endedAt == null

/**
 * The two accordions of the recent incidents: the ongoing ones, open by default, and the ones resolved within the last
 * day, closed by default. The [key] identifies them in `kuvasz.js`, which keeps the visitor's choice about them across
 * the refreshes of the page, see `applyIncidentsAccordionsState()`.
 */
private enum class IncidentsAccordion(
    val key: String,
    val isOpenByDefault: Boolean,
    val iconColor: CSSClass,
    val alertColor: CSSClass,
    val title: String,
) {
    ACTIVE(
        "active",
        isOpenByDefault = true,
        iconColor = TEXT_RED,
        alertColor = ALERT_DANGER,
        title = Messages.statusPageActiveIncidents(),
    ),
    RESOLVED(
        "resolved",
        isOpenByDefault = false,
        iconColor = TEXT_GREEN,
        alertColor = ALERT_SUCCESS,
        title = Messages.statusPageResolvedIncidents(),
    );

    val elementId: String = "status-page-$key-incidents"
    val collapseId: String = "$elementId-collapse"
}

/**
 * The recent incidents of the page below its overall status, in two accordions: the ongoing incidents, and the ones
 * resolved within the last day, one alert per monitor in each. An accordion is only there if it has any incident.
 */
internal fun FlowContent.statusPageIncidentsAccordions(pageData: StatusPageDataDto) {
    val resolvedSince = pageData.generatedAt.minusHours(RECENTLY_RESOLVED_HOURS)
    val monitors = pageData.monitors.sortedBy { it.name.lowercase() }
    val active = monitors.incidentsByMonitor { it.isOngoing }
    val resolved = monitors.incidentsByMonitor { it.endedAt?.isAfter(resolvedSince) == true }
    if (active.isEmpty() && resolved.isEmpty()) return

    div {
        classes(CONTAINER_XL, MB_4)
        incidentsAccordion(IncidentsAccordion.ACTIVE, active)
        incidentsAccordion(IncidentsAccordion.RESOLVED, resolved)
    }
}

private fun List<StatusPageMonitorDetailsDto>.incidentsByMonitor(
    predicate: (StatusPageIncidentDto) -> Boolean,
): List<Pair<StatusPageMonitorDetailsDto, List<StatusPageIncidentDto>>> =
    map { monitor -> monitor to monitor.incidentsOfHistory.filter(predicate) }
        .filter { (_, incidents) -> incidents.isNotEmpty() }

private fun FlowContent.incidentsAccordion(
    accordion: IncidentsAccordion,
    incidentsByMonitor: List<Pair<StatusPageMonitorDetailsDto, List<StatusPageIncidentDto>>>,
) {
    if (incidentsByMonitor.isEmpty()) return
    val isOpen = accordion.isOpenByDefault
    div {
        classes(CARD, MB_3)
        testId(accordion.elementId)
        div {
            id = accordion.elementId
            classes(ACCORDION, ACCORDION_FLUSH)
            div {
                classes(ACCORDION_ITEM)
                h2 {
                    classes(ACCORDION_HEADER)
                    button(type = ButtonType.button) {
                        classes(mutableSetOf(ACCORDION_BUTTON, PX_3).addIf(!isOpen, COLLAPSED))
                        testId("${accordion.elementId}-toggle")
                        collapseToggler()
                        dataBsTarget("#${accordion.collapseId}")
                        ariaExpanded(isOpen)
                        ariaControls(accordion.collapseId)
                        span {
                            classes(accordion.iconColor, ME_2)
                            icon(Icon.FLAME)
                        }
                        +accordion.title
                        span {
                            classes(BADGE, MS_2)
                            +incidentsByMonitor.sumOf { (_, incidents) -> incidents.size }.toString()
                        }
                        div {
                            classes(ACCORDION_BUTTON_TOGGLE)
                            icon(Icon.CHEVRON_DOWN)
                        }
                    }
                }
                div {
                    id = accordion.collapseId
                    classes(mutableSetOf(ACCORDION_COLLAPSE, COLLAPSE).addIf(isOpen, SHOW))
                    attributes["data-incidents-accordion"] = accordion.key
                    div {
                        classes(ACCORDION_BODY, PX_3, PT_0)
                        incidentsByMonitor.forEach { (monitor, incidents) ->
                            monitorIncidentsAlert(monitor, incidents, accordion.alertColor)
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.monitorIncidentsAlert(
    monitor: StatusPageMonitorDetailsDto,
    incidents: List<StatusPageIncidentDto>,
    color: CSSClass,
) {
    div {
        classes(ALERT, color)
        testId("status-page-incident-alert")
        attributes["role"] = "alert"
        h4 {
            classes(ALERT_HEADING)
            +monitor.name
        }
        incidents.forEach { incident -> publicIncident(incident) }
    }
}

/** An incident with its span and its public timeline, as the visitors of the status pages see it. */
private fun FlowContent.publicIncident(incident: StatusPageIncidentDto) {
    div {
        classes(MT_3)
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
            // The history of the status pages is counted in the system's time zone, so the times are shown in it too
            val time = node.time.toSystemZone()
            when (node) {
                is PublicTimelineNode.Started -> timelineEvent(
                    icon = Icon.FLAME,
                    color = Color.RED_LT,
                    time = time,
                    title = Messages.incidentStartedEvent(),
                    eventTestId = "public-incident-started",
                ) {}

                is PublicTimelineNode.Note -> timelineEvent(
                    icon = node.note.status.icon,
                    color = node.note.status.color,
                    time = time,
                    title = node.note.status.label,
                    eventTestId = "public-incident-note",
                ) {
                    noteContent(node.note)
                }

                is PublicTimelineNode.Resolved -> timelineEvent(
                    icon = Icon.CIRCLE_CHECK_FILLED,
                    color = Color.GREEN_LT,
                    time = time,
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
internal fun FlowContent.monitorIncidentsBadge(monitor: StatusPageMonitorDetailsDto, historyDays: Int) {
    val incidents = monitor.incidentsOfHistory
    if (incidents.isEmpty()) return
    a(href = "#") {
        classes(TEXT_RESET)
        testId("monitor-incidents-badge")
        modalOpener(STATUS_PAGE_INCIDENTS_MODAL_ID)
        attributes["data-incidents-of"] = monitor.incidentsKey
        onClick = "showStatusPageIncidents(this.dataset.incidentsOf)"
        inlineStatusBadge(
            text = incidents.size.toString(),
            color = Color.ORANGE_LT,
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
internal fun FlowContent.monitorIncidentsTemplate(monitor: StatusPageMonitorDetailsDto, historyDays: Int) {
    val incidents = monitor.incidentsOfHistory
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
            classes(MODAL_BODY, PT_0)
            testId("status-page-incidents-modal-body")
            incidents.forEach { incident -> publicIncident(incident) }
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
private fun StatusPageIncidentDto.spanText(): String {
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

// The history of the status pages is counted in the days of the system's time zone, so its times are shown in it too
internal fun OffsetDateTime.toSystemZone(): OffsetDateTime =
    atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
