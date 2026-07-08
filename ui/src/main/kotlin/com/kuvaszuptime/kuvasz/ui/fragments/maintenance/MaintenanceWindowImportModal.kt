package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.maintenanceWindowImportModal(modalId: String, labelsJson: String) {
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData("maintenanceWindowImportForm($labelsJson)")
        attributes["@maintenance-window-import-modal-closed.window"] = "resetState()"
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
                        +Messages.importMaintenanceWindowBackup()
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
                            +Messages.maintenanceWindowImportWarning()
                        }
                    }

                    div {
                        classes(MB_3, MT_3)
                        formLabel(
                            label = Messages.maintenanceWindowImportFileLabel(),
                            required = true,
                            inputName = "maintenance-window-import-file-input"
                        )
                        input {
                            id = "maintenance-window-import-file-input"
                            type = InputType.file
                            name = "file"
                            classes(FORM_CONTROL)
                            accept = ".yaml,.yml"
                            xOn("change", "handleFileChange(\$event)")
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
                            label = Messages.maintenanceWindowImportDryRunLabel(),
                            description = Messages.maintenanceWindowImportDryRunDescription(),
                            disabledIf = "importCompleted"
                        )
                    }

                    div {
                        xShow("result !== null")
                        classes(ALERT, MT_3)
                        testId("maintenance-window-import-result")
                        xBindClass(
                            "result?.dryRun === true " +
                                "? '${ALERT_INFO.className}' " +
                                ": '${ALERT_SUCCESS.className}'"
                        )
                        role = "alert"
                        div {
                            classes(ALERT_DESCRIPTION)
                            p {
                                xShow("result && result.receivedCnt === 0")
                                +Messages.maintenanceWindowImportResultEmpty()
                            }
                            templateTag {
                                xIf("result && result.receivedCnt > 0")
                                p {
                                    xText("formatResult(result)")
                                }
                            }
                        }
                    }

                    templateTag {
                        xIf("error")
                        div {
                            classes(ALERT, ALERT_DANGER, MT_3)
                            testId("maintenance-window-import-error")
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
                        testId("maintenance-window-import-submit-button")
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
    handleFormResetOnModalClose(modalId = modalId, eventName = "maintenance-window-import-modal-closed")
}
