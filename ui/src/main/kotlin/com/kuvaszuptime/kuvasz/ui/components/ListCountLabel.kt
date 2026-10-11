package com.kuvaszuptime.kuvasz.ui.components

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.listCountLabel(elementId: String, labelTestId: String, text: String?) {
    span {
        id = elementId
        classes(TEXT_SECONDARY)
        testId(labelTestId)
        if (text != null) {
            hx { swapOob() }
            +text
        }
    }
}
