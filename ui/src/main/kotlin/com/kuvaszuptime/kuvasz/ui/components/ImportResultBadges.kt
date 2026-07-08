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
    testIdSuffixExpr: String? = null,
) {
    div {
        xShow("($itemsExpr?.length ?? 0) > 0")
        classes(MT_2)
        if (testIdSuffixExpr != null) {
            attributes["x-bind:data-testid"] = "'$testId-' + ($testIdSuffixExpr)"
        } else {
            testId(testId)
        }
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
