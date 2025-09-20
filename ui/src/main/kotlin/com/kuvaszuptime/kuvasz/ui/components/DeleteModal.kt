package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.deleteModal(
    modalId: String,
    approvalQuestion: String,
    xOnApproval: String,
    isDeleteDisabled: Boolean = false,
) {
    div {
        id = modalId
        tabIndex = "-1"
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        role = "dialog"
        div {
            role = "document"
            classes(MODAL_DIALOG, MODAL_DIALOG_CENTERED, MODAL_SM)
            div {
                classes(MODAL_CONTENT)
                button {
                    type = ButtonType.button
                    classes(BTN_CLOSE)
                    modalCloser()
                    ariaLabel(Messages.close())
                }
                div {
                    classes(MODAL_STATUS, BG_DANGER)
                }
                div {
                    classes(MODAL_BODY, TEXT_CENTER, PY_4)
                    if (!isDeleteDisabled) {
                        h3 { +Messages.areYouSure() }
                    }
                    div {
                        classes(TEXT_SECONDARY)
                        +approvalQuestion
                    }
                }
                div {
                    classes(MODAL_FOOTER)
                    div {
                        classes(W_100)
                        div {
                            classes(ROW)
                            div {
                                classes(CSSClass.COL)
                                a("#") {
                                    classes(BTN, W_100)
                                    modalCloser()
                                    +Messages.cancel()
                                }
                            }
                            if (!isDeleteDisabled) {
                                div {
                                    classes(CSSClass.COL)
                                    button {
                                        type = ButtonType.button
                                        classes(BTN, BTN_DANGER, W_100)
                                        modalCloser()
                                        xOnClick(xOnApproval)
                                        icon(Icon.TRASH)
                                        +Messages.delete()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
