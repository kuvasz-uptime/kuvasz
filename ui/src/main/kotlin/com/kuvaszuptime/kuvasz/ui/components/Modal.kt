package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.entityLoadingOverlay() =
    loadingOverlay(xShowIf = "isLoadingEntity", overlayTestId = "entity-loading-overlay")

/**
 * The error the server reported for a save that the client-side validation let through (e.g. a constraint only the
 * backend can check). Without it such a response would leave the modal open with no explanation at all.
 */
internal fun FlowContent.formErrorAlert() {
    templateTag {
        xIf("formError")
        div {
            classes(ALERT, ALERT_DANGER, W_100, MB_0)
            testId("modal-form-error")
            xText("formError")
        }
    }
}

internal fun FlowContent.modalCancelButton(
    label: String = Messages.cancel(),
    closesModal: Boolean = true,
    extraClasses: Set<CSSClass> = emptySet(),
    block: A.() -> Unit = {},
) {
    a(href = "#") {
        classes(setOf(BTN, BTN_GHOST_SECONDARY) + extraClasses)
        if (closesModal) {
            modalCloser()
        }
        block()
        +label
    }
}

internal fun FlowContent.upsertModalFooter(
    isReadOnlyMode: Boolean,
    xSaveDisabledIf: String,
    xOnSaveClicked: String,
) {
    div {
        classes(MODAL_FOOTER, FLEX_WRAP)
        if (!isReadOnlyMode) {
            formErrorAlert()
        }
        modalCancelButton(label = if (isReadOnlyMode) Messages.close() else Messages.cancel()) {
            testId("modal-dismiss-button")
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
