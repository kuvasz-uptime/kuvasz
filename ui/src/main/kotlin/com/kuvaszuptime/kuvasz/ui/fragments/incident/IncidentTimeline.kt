package com.kuvaszuptime.kuvasz.ui.fragments.incident

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.isShownPublicly
import com.kuvaszuptime.kuvasz.models.label
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import java.time.OffsetDateTime

private const val TIMELINE_ID = "incident-timeline"

/**
 * The story of an incident in chronological order: its start, the integrations notified about it, its resolution, the
 * integrations notified about that, and its notes. It's refreshed out of
 * band ([swapOob]) together with [incidentDetailsHeading].
 */
internal fun FlowContent.incidentTimeline(
    details: IncidentDetailsDto,
    canEditNotes: Boolean,
    noteModalId: String,
    swapOob: Boolean = false,
) {
    val incident = details.incident
    val isPublicFlagShown = incident.incidentType.isShownPublicly
    val entries = buildList {
        add(TimelineEntry(incident.startedAt, TimelineEntry.Rank.STARTED) { startedEvent(details) })
        // Nothing is shown about the integrations if none of them was notified
        if (details.integrations.isNotEmpty()) {
            add(
                TimelineEntry(incident.startedAt, TimelineEntry.Rank.STARTED_INTEGRATIONS) {
                    notifiedIntegrationsEvent(
                        integrations = details.integrations,
                        time = incident.startedAt,
                        title = Messages.notifiedIntegrations(),
                        eventTestId = "incident-integrations-event",
                    )
                }
            )
        }
        incident.endedAt?.let { endedAt ->
            add(TimelineEntry(endedAt, TimelineEntry.Rank.RESOLVED) { resolvedEvent(details, endedAt) })
            details.resolutionIntegrations?.takeIf { it.isNotEmpty() }?.let { integrations ->
                add(
                    TimelineEntry(endedAt, TimelineEntry.Rank.RESOLVED_INTEGRATIONS) {
                        notifiedIntegrationsEvent(
                            integrations = integrations,
                            time = endedAt,
                            title = Messages.notifiedIntegrationsAboutResolution(),
                            eventTestId = "incident-resolution-integrations-event",
                        )
                    }
                )
            }
        }
        details.notes.forEach { note ->
            add(
                TimelineEntry(note.shownTime, TimelineEntry.Rank.NOTE) {
                    noteEvent(note, canEditNotes, noteModalId, isPublicFlagShown)
                }
            )
        }
    }
    div {
        id = TIMELINE_ID
        classes(ROW, ROW_CARDS)
        if (swapOob) hx { swapOob() }
        div {
            classes(COL_12)
            ul {
                classes(TIMELINE)
                testId("incident-timeline")
                // The edit buttons of the notes dispatch their events from here
                xData("{}")
                // The sort is stable, so the notes of the very same time keep their own order
                entries
                    .sortedWith(compareBy<TimelineEntry> { it.time.toInstant() }.thenBy { it.rank })
                    .forEach { entry -> entry.render(this) }
            }
        }
    }
}

/**
 * An event of the timeline, ordered by its [time]. At the very same time the events of the incident itself come first,
 * in the order of their [rank], and the notes after them.
 */
private class TimelineEntry(val time: OffsetDateTime, val rank: Rank, val render: UL.() -> Unit) {
    enum class Rank { STARTED, STARTED_INTEGRATIONS, RESOLVED, RESOLVED_INTEGRATIONS, NOTE }
}

// The timestamp of a note overrides its creation date, see the ordering of the notes in the repository too
private val IncidentNoteDto.shownTime: OffsetDateTime
    get() = timestamp ?: createdAt

private fun UL.startedEvent(details: IncidentDetailsDto) {
    timelineEvent(
        icon = Icon.FLAME,
        color = Color.RED_LT,
        time = details.incident.startedAt,
        title = Messages.incidentStartedEvent(),
        eventTestId = "incident-started-event",
    ) {
        details.incident.details?.let { error ->
            p {
                classes(TEXT_SECONDARY, TEXT_BREAK, MB_0)
                +error
            }
        }
    }
}

private fun UL.resolvedEvent(details: IncidentDetailsDto, endedAt: OffsetDateTime) {
    val incident = details.incident
    timelineEvent(
        icon = Icon.CIRCLE_CHECK_FILLED,
        color = Color.GREEN_LT,
        time = endedAt,
        title = Messages.incidentResolvedEvent(),
        eventTestId = "incident-resolved-event",
    ) {
        p {
            classes(TEXT_SECONDARY, MB_0)
            +Messages.incidentLasted(
                getDurationOfEvent(
                    isMonitorEnabled = incident.isMonitorEnabled,
                    startedAt = incident.startedAt,
                    endedAt = incident.endedAt,
                    updatedAt = incident.updatedAt,
                ).formatAsInterval()
            )
        }
    }
}

private fun UL.notifiedIntegrationsEvent(
    integrations: List<IntegrationID>,
    time: OffsetDateTime,
    title: String,
    eventTestId: String,
) {
    timelineEvent(icon = Icon.BELL, color = Color.BLUE_LT, time = time, title = title, eventTestId = eventTestId) {
        div {
            classes(D_FLEX, FLEX_WRAP, GAP_2)
            integrations.sortedBy { it.toString().lowercase() }.forEach { integration ->
                span {
                    testId("notified-integration")
                    inlineStatusBadge(text = integration.toString(), icon = integration.type.icon)
                }
            }
        }
    }
}

private fun UL.noteEvent(
    note: IncidentNoteDto,
    canEdit: Boolean,
    noteModalId: String,
    isPublicFlagShown: Boolean,
) {
    timelineEvent(
        icon = note.status.icon,
        color = note.status.color,
        time = note.shownTime,
        title = note.status.label,
        eventTestId = "incident-note-event",
        titleBadges = {
            if (isPublicFlagShown) {
                span {
                    classes(MS_2)
                    testId("incident-note-visibility")
                    if (note.public) {
                        inlineStatusBadge(text = Messages.notePublic(), icon = Icon.EYE, color = Color.GREEN_LT)
                    } else {
                        inlineStatusBadge(text = Messages.notePrivate(), icon = Icon.EYE_OFF)
                    }
                }
            }
        },
        actions = if (canEdit) {
            {
                compactIconButton(Icon.EDIT, classes = setOf(BTN_SM, MS_2)) {
                    testId("incident-note-edit-button")
                    ariaLabel(Messages.editNote())
                    modalOpener(noteModalId)
                    xOnClick("\$dispatch('$EDIT_INCIDENT_NOTE_EVENT', ${note.asJsonString()})")
                }
            }
        } else {
            null
        },
    ) {
        p {
            classes(TEXT_PRE_LINE, TEXT_BREAK, MB_0)
            testId("incident-note-content")
            +note.content
        }
        // Editing never reorders a note, but it's worth knowing that it has been changed since
        if (note.updatedAt.isAfter(note.createdAt)) {
            div {
                classes(TEXT_SECONDARY, MT_2)
                testId("incident-note-edited")
                +Messages.noteEdited(note.updatedAt.toSystemZone().toDateTimeStringWithZone())
            }
        }
    }
}

/**
 * A node of an incident's timeline, shared with the public timeline of the status pages, which only leaves out the
 * [titleBadges] and the [actions].
 */
internal fun UL.timelineEvent(
    icon: Icon,
    color: Color,
    time: OffsetDateTime,
    title: String,
    eventTestId: String,
    titleBadges: (FlowContent.() -> Unit)? = null,
    actions: (FlowContent.() -> Unit)? = null,
    body: FlowContent.() -> Unit,
) {
    li {
        classes(TIMELINE_EVENT)
        testId(eventTestId)
        div {
            classes(TIMELINE_EVENT_ICON, color.bgColor, color.textColor)
            icon(icon)
        }
        div {
            classes(CARD, TIMELINE_EVENT_CARD)
            div {
                classes(CARD_BODY)
                div {
                    classes(D_FLEX, ALIGN_ITEMS_START, MB_2)
                    div {
                        div {
                            classes(D_FLEX, ALIGN_ITEMS_CENTER, FLEX_WRAP)
                            h4 {
                                classes(MB_0)
                                +title
                            }
                            titleBadges?.invoke(this)
                        }
                        div {
                            classes(D_FLEX, ALIGN_ITEMS_CENTER, GAP_1, ICON_INLINE, TEXT_SECONDARY, MT_1)
                            icon(Icon.CLOCK)
                            span {
                                testId("timeline-event-time")
                                +time.toSystemZone().toDateTimeStringWithZone()
                            }
                        }
                    }
                    actions?.let { renderActions ->
                        div {
                            classes(MS_AUTO)
                            renderActions()
                        }
                    }
                }
                body()
            }
        }
    }
}

internal val InvestigationStatus.icon: Icon
    get() = when (this) {
        InvestigationStatus.INVESTIGATING -> Icon.BINOCULARS
        InvestigationStatus.IDENTIFIED -> Icon.VIEWFINDER
        InvestigationStatus.RESOLVED -> Icon.CIRCLE_CHECK_FILLED
    }

internal val InvestigationStatus.color: Color
    get() = when (this) {
        InvestigationStatus.INVESTIGATING -> Color.ORANGE_LT
        InvestigationStatus.IDENTIFIED -> Color.BLUE_LT
        InvestigationStatus.RESOLVED -> Color.GREEN_LT
    }
