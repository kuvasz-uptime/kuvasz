package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.accordion(
    id: String,
    content: FlowContent.() -> Unit,
) {
    div {
        this.id = id
        classes(ACCORDION, ACCORDION_FLUSH)
        role = "tablist"
        attributes["aria-multiselectable"] = "true"
        content()
    }
}

internal fun FlowContent.accordionItem(
    id: String,
    parentId: String,
    title: String,
    titleIcon: Icon? = null,
    additionalTitleContent: FlowContent.() -> Unit = {},
    showInitially: Boolean = false,
    content: FlowContent.() -> Unit,
) {
    div {
        classes(ACCORDION_ITEM)
        h3 {
            classes(ACCORDION_HEADER)
            button {
                val classes = mutableSetOf(ACCORDION_BUTTON, PX_0).addIf(!showInitially, COLLAPSED)
                classes(classes)
                collapseToggler()
                dataBsTarget("#$id")
                testId("accordion-toggle-$id")
                titleIcon?.let { icon(it) }
                +title
                additionalTitleContent()
                div {
                    classes(ACCORDION_BUTTON_TOGGLE)
                    icon(Icon.CHEVRON_DOWN)
                }
            }
        }
        div {
            this.id = id
            val classes = mutableSetOf(ACCORDION_COLLAPSE, COLLAPSE).addIf(showInitially, SHOW)
            classes(classes)
            role = "tabpanel"
            dataBsParent("#$parentId")
            div {
                classes(PT_0, PX_0, PB_3)
                content()
            }
        }
    }
}
