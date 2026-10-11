package com.kuvaszuptime.kuvasz.ui.pages.incident

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.incident.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

private const val NOTE_MODAL_ID = "incident-note-modal"

private val AppGlobals.canEditIncidentNotes: Boolean
    get() = !editabilityState.areIncidentNotesReadOnly

fun renderIncidentDetailsPage(globals: AppGlobals, details: IncidentDetailsDto): String =
    withLayout(
        globals,
        title = Messages.incidentOfMonitor(details.incident.monitorName.abbreviate(MONITOR_NAME_MAX_LENGTH)),
        pageTitle = { incidentDetailsHeader(details, canEditNotes = globals.canEditIncidentNotes) },
    ) {
        incidentTimeline(details, canEditNotes = globals.canEditIncidentNotes, noteModalId = NOTE_MODAL_ID)
    }

/**
 * The refreshed heading of an incident's details page, along with its timeline swapped out of band.
 */
fun renderIncidentDetailsFragment(globals: AppGlobals, details: IncidentDetailsDto): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).div {
        incidentDetailsHeading(details.incident)
        incidentTimeline(
            details,
            canEditNotes = globals.canEditIncidentNotes,
            noteModalId = NOTE_MODAL_ID,
            swapOob = true,
        )
    }

private fun HtmlBlockTag.incidentDetailsHeader(details: IncidentDetailsDto, canEditNotes: Boolean) {
    div {
        classes(CONTAINER)
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            incidentDetailsHeading(details.incident)
            if (canEditNotes) {
                div {
                    classes(COL_MD_AUTO, MS_AUTO)
                    div {
                        classes(BTN_LIST)
                        buttonWithIcon(Icon.PLUS, Messages.addNote(), classes = setOf(BTN_PRIMARY)) {
                            testId("add-incident-note-button")
                            modalOpener(NOTE_MODAL_ID)
                        }
                    }
                    incidentNoteModal(NOTE_MODAL_ID, details.incident)
                }
            }
        }
    }
}
