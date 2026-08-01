package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The modal that imports a YAML backup of some kind of entity. Everything but the summary of what was imported is the
 * same regardless of what is being restored, so [importResult] is where a caller renders the shape its own API returns.
 *
 * [slug] is the kebab-case name of the entity, e.g. `status-page`: it names the DOM ids, the test ids and the event the
 * modal resets itself on, and has to match what the Alpine.js component behind [alpineForm] expects.
 */
internal fun FlowContent.importModal(
    modalId: String,
    slug: String,
    alpineForm: String,
    title: String,
    warning: String,
    fileLabel: String,
    dryRunLabel: String,
    dryRunDescription: String,
    importResult: FlowContent.() -> Unit,
) {
    val fileInputId = "$slug-import-file-input"
    val closedEventName = "$slug-import-modal-closed"

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(alpineForm)
        attributes["@$closedEventName.window"] = "resetState()"
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
                        +title
                    }
                    button(type = ButtonType.button) {
                        classes(BTN_CLOSE)
                        modalCloser()
                    }
                }

                div {
                    classes(MODAL_BODY, PB_0)

                    div {
                        classes(ALERT, ALERT_WARNING)
                        role = "alert"
                        div {
                            classes(ALERT_ICON)
                            icon(Icon.ALERT_TRIANGLE)
                        }
                        div {
                            classes(ALERT_DESCRIPTION)
                            +warning
                        }
                    }

                    div {
                        classes(MB_3, MT_3)
                        formLabel(label = fileLabel, required = true, inputName = fileInputId)
                        input {
                            id = fileInputId
                            type = InputType.file
                            name = "file"
                            classes(FORM_CONTROL)
                            accept = ".yaml,.yml"
                            xOnChange("handleFileChange(\$event)")
                            xBindDisabled("importCompleted")
                        }
                        templateTag {
                            xIf("errors.file")
                            div {
                                classes(INVALID_FEEDBACK)
                                xText("errors.file")
                            }
                        }
                    }

                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "dryRun",
                            label = dryRunLabel,
                            description = dryRunDescription,
                            disabledIf = "importCompleted"
                        )
                    }

                    div {
                        xShow("result !== null")
                        classes(ALERT, MT_3)
                        testId("$slug-import-result")
                        xBindClass(
                            "result?.dryRun === true " +
                                "? '${ALERT_INFO.className}' " +
                                ": '${ALERT_SUCCESS.className}'"
                        )
                        role = "alert"
                        div {
                            classes(ALERT_DESCRIPTION)
                            importResult()
                        }
                    }

                    templateTag {
                        xIf("error")
                        div {
                            classes(ALERT, ALERT_DANGER, MT_3)
                            testId("$slug-import-error")
                            role = "alert"
                            div {
                                classes(ALERT_DESCRIPTION)
                                xText("error")
                            }
                        }
                    }
                }

                div {
                    classes(MODAL_FOOTER)
                    a(href = "#") {
                        xShow("!importCompleted")
                        classes(BTN, BTN_LINK, LINK_SECONDARY)
                        modalCloser()
                        +Messages.cancel()
                    }
                    button {
                        xShow("!importCompleted")
                        classes(BTN, BTN_PRIMARY, MS_AUTO)
                        testId("$slug-import-submit-button")
                        xBindDisabled("!file || isRequestLoading")
                        xOnClick("submitForm()")
                        icon(Icon.UPLOAD)
                        span {
                            xText("submitButtonLabel")
                        }
                    }
                    a(href = "#") {
                        xShow("importCompleted")
                        classes(BTN, BTN_PRIMARY, MS_AUTO)
                        modalCloser()
                        +Messages.close()
                    }
                }
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = closedEventName)
}

/**
 * The result summary of an import that returns a single, flat result -- as opposed to one broken down per entity type.
 */
internal fun FlowContent.flatImportResult(
    emptyText: String,
    badgeLists: FlowContent.() -> Unit,
) {
    p {
        xShow("result && result.receivedCnt === 0")
        +emptyText
    }
    templateTag {
        xIf("result && result.receivedCnt > 0")
        div {
            p {
                xText("formatResult(result)")
            }
            badgeLists()
        }
    }
}
