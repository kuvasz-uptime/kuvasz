package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.importResultBadgeList(
    itemsExpr: String,
    label: String,
    color: Color,
    testId: String,
) {
    div {
        xShow("($itemsExpr?.length ?? 0) > 0")
        classes(MT_2)
        testId(testId)
        div {
            classes(MB_2)
            strong { +label }
        }
        templateTag {
            xFor("item in $itemsExpr")
            span {
                classes(BADGE, color.bgColor, color.textColor, ME_2, MB_2)
                xText("item")
            }
        }
    }
}
