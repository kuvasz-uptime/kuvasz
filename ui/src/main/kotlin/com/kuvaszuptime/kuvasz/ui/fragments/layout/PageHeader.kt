package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun HtmlBlockTag.simplePageHeader(preTitle: String, title: String) {
    div {
        classes(CONTAINER_XL)
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER)
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +preTitle
                }
                h2 {
                    classes(PAGE_TITLE)
                    +title
                }
            }
        }
    }
}
