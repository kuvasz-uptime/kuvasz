package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun FlowContent.statusSummary(pageData: StatusPageDataDto) {
    div {
        classes(CONTAINER)
        div {
            classes(CSSClass.COL)
            div {
                classes(ROW, ALIGN_ITEMS_CENTER, TEXT_CENTER, MB_2)
                span {
                    classes(pageData.systemStatus.color())
                    icon(pageData.systemStatus.icon())
                }
            }
            div {
                classes(ROW, ALIGN_ITEMS_CENTER, TEXT_CENTER)
                h1 {
                    +pageData.systemStatus.title()
                }
                p {
                    classes(TEXT_MUTED)
                    +pageData.systemStatus.description()
                }
            }
        }
    }
}
