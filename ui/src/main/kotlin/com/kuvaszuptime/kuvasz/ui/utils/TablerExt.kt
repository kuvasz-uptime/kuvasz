@file:Suppress("MatchingDeclarationName")

package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.*

internal enum class TooltipLocation {
    BOTTOM,
    RIGHT,
}

internal fun HTMLTag.tooltip(title: String, location: TooltipLocation = TooltipLocation.BOTTOM) {
    tooltipToggler()
    attributes["data-bs-placement"] = location.name.lowercase()
    attributes["title"] = title
}

internal fun HTMLTag.modalOpener(modalId: String) {
    modalToggler()
    dataBsTarget("#$modalId")
}

internal fun HTMLTag.modalCloser() {
    attributes["data-bs-dismiss"] = "modal"
}

internal fun HTMLTag.alertCloser() {
    attributes["data-bs-dismiss"] = "alert"
    ariaLabel("close")
}

internal fun HTMLTag.dataBsToggle(toggle: String) {
    attributes["data-bs-toggle"] = toggle
}

internal fun HTMLTag.dropdownToggler() = dataBsToggle("dropdown")
internal fun HTMLTag.collapseToggler() = dataBsToggle("collapse")
internal fun HTMLTag.modalToggler() = dataBsToggle("modal")
internal fun HTMLTag.tooltipToggler() = dataBsToggle("tooltip")

internal fun HTMLTag.dataBsTarget(target: String) {
    attributes["data-bs-target"] = target
}

internal fun HTMLTag.dataBsParent(parent: String) {
    attributes["data-bs-parent"] = parent
}

internal fun HTMLTag.enableMasonry() {
    attributes["data-masonry"] = "{\"percentPosition\": true }"
}
