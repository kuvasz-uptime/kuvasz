package com.kuvaszuptime.kuvasz.ui.fragments.incident

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.isShownPublicly
import com.kuvaszuptime.kuvasz.models.label
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

// Dispatched with the note to edit as the detail, see the timeline of the incident
internal const val EDIT_INCIDENT_NOTE_EVENT = "edit-incident-note"
private const val NOTE_MODAL_CLOSED_EVENT = "incident-note-modal-closed"

/**
 * The modal that adds a note to the [incident], or edits (and deletes) one of its notes when it's opened from the
 * timeline. It's reset to the creation of a new note whenever it's closed.
 */
internal fun FlowContent.incidentNoteModal(modalId: String, incident: IncidentDto) {
    val serializedErrorMessages = mapOf(
        "contentRequired" to Messages.errorNoteContentRequired(),
        "timestampInvalid" to Messages.errorNoteTimestampInvalid(),
    ).asJsonString()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        testId("incident-note-modal")
        xData("incidentNoteForm('${incident.incidentType.name}', ${incident.eventId}, $serializedErrorMessages)")
        xOn("$NOTE_MODAL_CLOSED_EVENT.window", "resetState()")
        xOn("$EDIT_INCIDENT_NOTE_EVENT.window", "editFrom(\$event.detail)")
        tabIndex = "-1"
        role = "dialog"

        div {
            classes(MODAL_DIALOG, MODAL_DIALOG_CENTERED)
            role = "document"
            div {
                classes(MODAL_CONTENT)
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        span {
                            xShow("!isUpdate")
                            +Messages.addNote()
                        }
                        span {
                            xShow("isUpdate")
                            +Messages.editNote()
                        }
                    }
                    button(type = ButtonType.button) {
                        classes(BTN_CLOSE)
                        modalCloser()
                    }
                }
                div {
                    classes(MODAL_BODY, PB_0)
                    div {
                        classes(MB_3)
                        formLabel(label = Messages.noteStatusLabel(), required = true)
                        selectGroup(
                            xModelName = "status",
                            readOnly = false,
                            values = InvestigationStatus.entries.map { ValueAndLabel(it.name, it.label) },
                        )
                    }
                    div {
                        classes(MB_3)
                        validatedTextArea(
                            propName = "content",
                            label = Messages.noteContentLabel(),
                            placeholder = Messages.noteContentPlaceholder(),
                            required = true,
                            onInput = "validateContent()",
                        )
                    }
                    div {
                        classes(MB_3)
                        dateTimePickerInput(
                            inputName = "timestamp-input",
                            errorProp = "timestamp",
                            label = Messages.noteTimestampLabel(),
                            description = Messages.noteTimestampDescription(),
                        )
                    }
                    if (incident.incidentType.isShownPublicly) {
                        div {
                            classes(MB_3)
                            toggleSwitch(
                                propName = "public",
                                label = Messages.notePublicLabel(),
                                description = Messages.notePublicDescription(),
                            )
                        }
                    }
                }
                noteModalFooter()
            }
        }
    }
    handleFormResetOnModalClose(modalId, NOTE_MODAL_CLOSED_EVENT)
}

/**
 * Cancel and save, plus the deletion of an edited note, which has to be confirmed in place, instead of in another
 * modal opened on top of this one.
 */
private fun FlowContent.noteModalFooter() {
    div {
        classes(MODAL_FOOTER, FLEX_WRAP)
        formErrorAlert()
        templateTag {
            xIf("isUpdate && !isDeleteConfirmationShown")
            button(type = ButtonType.button) {
                classes(BTN, BTN_OUTLINE_DANGER)
                testId("incident-note-delete-button")
                xBindDisabled("isRequestLoading")
                xOnClick("isDeleteConfirmationShown = true")
                icon(Icon.TRASH)
                +Messages.deleteNote()
            }
        }
        templateTag {
            xIf("isDeleteConfirmationShown")
            div {
                classes(D_FLEX, ALIGN_ITEMS_CENTER, FLEX_WRAP, GAP_2, W_100)
                span {
                    classes(TEXT_SECONDARY)
                    +Messages.deleteNoteConfirmation()
                }
                modalCancelButton(closesModal = false, extraClasses = setOf(MS_AUTO)) {
                    testId("incident-note-delete-cancel-button")
                    xOnClick("\$event.preventDefault(); isDeleteConfirmationShown = false")
                }
                button(type = ButtonType.button) {
                    classes(BTN, BTN_DANGER)
                    testId("incident-note-delete-confirm-button")
                    xBindDisabled("isRequestLoading")
                    xOnClick("deleteNote()")
                    icon(Icon.TRASH)
                    +Messages.deleteNote()
                }
            }
        }
        templateTag {
            xIf("!isDeleteConfirmationShown")
            div {
                classes(D_FLEX, ALIGN_ITEMS_CENTER, GAP_2, MS_AUTO)
                modalCancelButton {
                    testId("modal-dismiss-button")
                }
                button(type = ButtonType.button) {
                    classes(BTN, BTN_PRIMARY)
                    testId("modal-save-button")
                    xBindDisabled("isRequestLoading")
                    xOnClick("submitForm()")
                    icon(Icon.FLOPPY)
                    +Messages.save()
                }
            }
        }
    }
}
