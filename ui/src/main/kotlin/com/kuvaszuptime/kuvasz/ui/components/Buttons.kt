package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.compactIconButton(
    icon: Icon,
    classes: Set<CSSClass> = emptySet(),
    onClick: String? = null,
    content: HTMLTag.() -> Unit = {}
) {
    compactIconButton(classes = classes, onClick = onClick) {
        content()
        icon(icon)
    }
}

internal fun FlowContent.compactIconButton(
    classes: Set<CSSClass> = emptySet(),
    onClick: String? = null,
    content: HTMLTag.() -> Unit = {}
) {
    div {
        classes(setOf(BTN, BTN_ICON) + classes)
        onClick?.let { this.onClick = it }
        content()
    }
}

internal fun FlowContent.buttonWithIcon(
    icon: Icon,
    label: String,
    classes: Set<CSSClass> = emptySet(),
    content: HTMLTag.() -> Unit = {}
) {
    button {
        classes(classes + BTN)
        content()
        icon(icon)
        +label
    }
}
