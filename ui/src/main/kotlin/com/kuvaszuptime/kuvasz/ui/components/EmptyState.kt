package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.emptyState(icon: Icon, title: String, subtitle: String) {
    div {
        classes(EMPTY)
        testId("empty-state")
        div {
            classes(EMPTY_ICON)
            icon(icon)
        }
        p {
            classes(EMPTY_TITLE)
            +title
        }
        p {
            classes(EMPTY_SUBTITLE, TEXT_SECONDARY)
            +subtitle
        }
    }
}
