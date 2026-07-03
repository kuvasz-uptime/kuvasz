package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.formLabel
import com.kuvaszuptime.kuvasz.ui.components.toggleSwitch
import com.kuvaszuptime.kuvasz.ui.icons.Icon
import com.kuvaszuptime.kuvasz.ui.icons.icon
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.monitorImportModal(modalId: String, labelsJson: String) {
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData("monitorImportForm($labelsJson)")
        attributes["@monitor-import-modal-closed.window"] = "resetState()"
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
                        +Messages.importMonitorBackup()
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
                            +Messages.monitorImportWarning()
                        }
                    }

                    div {
                        classes(MB_3, MT_3)
                        formLabel(
                            label = Messages.monitorImportFileLabel(),
                            required = true,
                            inputName = "monitor-import-file-input"
                        )
                        input {
                            id = "monitor-import-file-input"
                            type = InputType.file
                            name = "file"
                            classes(FORM_CONTROL)
                            accept = ".yaml,.yml"
                            xOn("change", "handleFileChange(\$event)")
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
                            label = Messages.monitorImportDryRunLabel(),
                            description = Messages.monitorImportDryRunDescription()
                        )
                    }

                    div {
                        xShow("result !== null")
                        classes(ALERT, MT_3)
                        xBindClass(
                            "result?.dryRun === true " +
                                "? '${CSSClass.ALERT_INFO.className}' " +
                                ": '${CSSClass.ALERT_SUCCESS.className}'"
                        )
                        role = "alert"
                        div {
                            classes(ALERT_DESCRIPTION)
                            p {
                                strong { +Messages.monitorImportResultReceived() }
                                +" "
                                span { xText("result.receivedMonitorCnt") }
                            }
                            p {
                                strong { +Messages.monitorImportResultImported() }
                                +" "
                                span { xText("result.importedMonitorCnt") }
                            }
                            p {
                                strong { +Messages.monitorImportResultDeleted() }
                                +" "
                                span { xText("result.deletedMonitorCount") }
                            }
                            div {
                                classes(MT_2)
                                xShow("result && result.perTypeResults.length > 0")
                                p {
                                    strong { +Messages.monitorImportResultPerType() }
                                }
                                ul {
                                    templateTag {
                                        xFor("typeResult in result.perTypeResults")
                                        liTag {
                                            xText("formatTypeResult(typeResult)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    templateTag {
                        xIf("error")
                        div {
                            classes(ALERT, ALERT_DANGER, MT_3)
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
                        classes(BTN, BTN_LINK, LINK_SECONDARY)
                        modalCloser()
                        +Messages.cancel()
                    }
                    button {
                        classes(BTN, BTN_PRIMARY, MS_AUTO)
                        xBindDisabled("!file || isRequestLoading")
                        xOnClick("submitForm()")
                        icon(Icon.UPLOAD)
                        span {
                            xText("submitButtonLabel")
                        }
                    }
                }
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = "monitor-import-modal-closed")
}
