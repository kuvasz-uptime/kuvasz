package com.kuvaszuptime.kuvasz.ui.fragments.incident

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

private const val HEADING_ID = "incident-detail-heading"

internal fun incidentDetailsFragmentPath(incident: IncidentDto) =
    "/incidents/fragments/details/${incident.incidentType.name.lowercase()}/${incident.eventId}"

/**
 * The heading of an incident's details page: its status, the monitor it belongs to, and a badge telling apart the
 * incident from the monitor itself. While the incident is ongoing, it refreshes itself and the timeline below it, so a
 * resolution shows up without reloading the page. The response bringing the resolution stops the refreshing too.
 */
internal fun FlowContent.incidentDetailsHeading(incident: IncidentDto) {
    div {
        id = HEADING_ID
        classes(COL_AUTO)
        if (incident.status == IncidentStatus.ONGOING) {
            hx {
                get(incidentDetailsFragmentPath(incident))
                // The fragment is this whole element again, so only its children are swapped in to avoid nesting it.
                // The timeline arrives in the same response, swapped out of band
                select("#$HEADING_ID > *")
                trigger {
                    every(15.seconds)
                }
                onSwapReinitTooltips()
            }
        }
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_AUTO)
                incidentStatusIndicator(incident)
            }
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +"#${incident.eventId}"
                }
                h2 {
                    classes(PAGE_TITLE, TEXT_WRAP, TEXT_BREAK)
                    +incident.monitorName.abbreviate(MONITOR_NAME_MAX_LENGTH)
                }
                div {
                    classes(TEXT_SECONDARY)
                    ul {
                        classes(LIST_INLINE, MT_1, MB_0)
                        a(href = incident.getMonitorUrl()) {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            testId("incident-monitor-badge")
                            inlineStatusBadge(
                                text = incident.incidentType.label,
                                icon = incident.incidentType.icon,
                                color = incident.incidentType.color,
                            )
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            testId("incident-badge")
                            inlineStatusBadge(text = Messages.incident(), icon = Icon.FLAME, color = Color.RED_LT)
                        }
                    }
                }
            }
        }
    }
}

/** A pulsing red indicator while the incident is ongoing, and a steady green one after it has been resolved. */
private fun FlowContent.incidentStatusIndicator(incident: IncidentDto) {
    val isOngoing = incident.status == IncidentStatus.ONGOING
    span {
        classes(
            if (isOngoing) {
                setOf(STATUS_INDICATOR, STATUS_RED, STATUS_INDICATOR_ANIMATED)
            } else {
                setOf(STATUS_INDICATOR, STATUS_GREEN)
            }
        )
        testId("incident-status-indicator")
        tooltip(if (isOngoing) Messages.dashboardIncidentOngoing() else Messages.dashboardIncidentResolved())
        uptimeStatusDot(withBadge = false, label = "", animated = isOngoing)
    }
}
