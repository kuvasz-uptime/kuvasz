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
