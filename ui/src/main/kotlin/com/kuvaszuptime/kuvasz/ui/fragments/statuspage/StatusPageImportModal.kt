package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.statusPageImportModal(modalId: String, labelsJson: String) {
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData("statusPageImportForm($labelsJson)")
        attributes["@status-page-import-modal-closed.window"] = "resetState()"
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
                        +Messages.importStatusPageBackup()
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
                            +Messages.statusPageImportWarning()
                        }
                    }

                    div {
                        classes(MB_3, MT_3)
                        formLabel(
                            label = Messages.statusPageImportFileLabel(),
                            required = true,
                            inputName = "status-page-import-file-input"
                        )
                        input {
                            id = "status-page-import-file-input"
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
                            label = Messages.statusPageImportDryRunLabel(),
                            description = Messages.statusPageImportDryRunDescription(),
                            disabledIf = "importCompleted"
                        )
                    }

                    div {
                        xShow("result !== null")
                        classes(ALERT, MT_3)
                        testId("status-page-import-result")
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
                                +Messages.statusPageImportResultEmpty()
                            }
                            templateTag {
                                xIf("result && result.receivedCnt > 0")
                                div {
                                    p {
                                        xText("formatResult(result)")
                                    }
                                    importResultBadgeList(
                                        itemsExpr = "result.imported",
                                        label = Messages.statusPageImportResultImportedLabel(),
                                        color = Color.GREEN_LT,
                                        testId = "status-page-import-result-imported",
                                    )
                                    importResultBadgeList(
                                        itemsExpr = "result.deleted",
                                        label = Messages.statusPageImportResultDeletedLabel(),
                                        color = Color.RED_LT,
                                        testId = "status-page-import-result-deleted",
                                    )
                                    importResultBadgeList(
                                        itemsExpr = "result.ignoredMonitors",
                                        label = Messages.statusPageImportResultIgnoredMonitorsLabel(),
                                        color = Color.YELLOW_LT,
                                        testId = "status-page-import-result-ignored-monitors",
                                    )
                                }
                            }
                        }
                    }

                    templateTag {
                        xIf("error")
                        div {
                            classes(ALERT, ALERT_DANGER, MT_3)
                            testId("status-page-import-error")
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
                        testId("status-page-import-submit-button")
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
    handleFormResetOnModalClose(modalId = modalId, eventName = "status-page-import-modal-closed")
}
