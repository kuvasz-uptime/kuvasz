package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun FlowContent.upsertModalFooter(
    isReadOnlyMode: Boolean,
    xSaveDisabledIf: String,
    xOnSaveClicked: String,
) {
    div {
        classes(MODAL_FOOTER)
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
