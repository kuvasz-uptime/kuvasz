package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun FlowContent.htmxLoadingIndicator() {
    div {
        classes(HTMX_INDICATOR, D_FLEX, JUSTIFY_CONTENT_CENTER, MY_5)
        div {
            classes(SPINNER_GROW)
            role = "status"
        }
    }
}

/**
 * An overlay with a spinner, covering its closest positioned ancestor while the Alpine.js [xShowIf] expression holds.
 */
internal fun FlowContent.loadingOverlay(xShowIf: String, overlayTestId: String) {
    templateTag {
        xIf(xShowIf)
        div {
            classes(
                POSITION_ABSOLUTE, TOP_0, START_0, END_0, BOTTOM_0,
                D_FLEX, ALIGN_ITEMS_CENTER, JUSTIFY_CONTENT_CENTER,
                BG_BODY, BG_OPACITY_75, ROUNDED
            )
            testId(overlayTestId)
            div {
                classes(SPINNER_GROW)
                role = "status"
            }
        }
    }
}
