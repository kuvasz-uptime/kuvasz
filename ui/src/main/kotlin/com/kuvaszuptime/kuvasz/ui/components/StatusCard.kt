package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/** A card with a stripe in the [statusColor] along its left edge, telling the state of what it shows. */
internal fun FlowContent.statusCard(statusColor: CSSClass, cardTestId: String, block: DIV.() -> Unit) {
    div {
        classes(CARD)
        testId(cardTestId)
        div {
            classes(CARD_STATUS_START, statusColor)
        }
        div {
            classes(CARD_BODY)
            block()
        }
    }
}
