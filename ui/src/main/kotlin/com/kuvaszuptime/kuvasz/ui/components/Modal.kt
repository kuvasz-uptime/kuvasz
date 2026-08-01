package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun FlowContent.cloningOverlay() {
    templateTag {
        xIf("isCloning")
        div {
            classes(
                POSITION_ABSOLUTE, TOP_0, START_0, END_0, BOTTOM_0,
                D_FLEX, ALIGN_ITEMS_CENTER, JUSTIFY_CONTENT_CENTER,
                BG_SURFACE_BACKDROP, ROUNDED
            )
            testId("cloning-overlay")
            div {
                classes(SPINNER_GROW)
                role = "status"
            }
        }
    }
}

/**
 * The error the server reported for a save that the client-side validation let through (e.g. a constraint only the
 * backend can check). Without it such a response would leave the modal open with no explanation at all.
 */
fun FlowContent.formErrorAlert() {
    templateTag {
        xIf("formError")
        div {
            classes(ALERT, ALERT_DANGER, W_100, MB_0)
            testId("modal-form-error")
            xText("formError")
        }
    }
}

fun FlowContent.upsertModalFooter(
    isReadOnlyMode: Boolean,
    xSaveDisabledIf: String,
    xOnSaveClicked: String,
) {
    div {
        classes(MODAL_FOOTER, FLEX_WRAP)
        if (!isReadOnlyMode) {
            formErrorAlert()
        }
        a(href = "#") {
            classes(BTN, BTN_LINK, LINK_SECONDARY)
            modalCloser()
            testId("modal-dismiss-button")
            if (isReadOnlyMode) {
                +Messages.close()
            } else {
                +Messages.cancel()
            }
        }
        if (!isReadOnlyMode) {
            button {
                classes(BTN, BTN_PRIMARY, MS_AUTO)
                xBindDisabled(xSaveDisabledIf)
                xOnClick(xOnSaveClicked)
                testId("modal-save-button")
                icon(Icon.FLOPPY)
                +Messages.save()
            }
        }
    }
}
