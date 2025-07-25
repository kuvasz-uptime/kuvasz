@file:Suppress("MatchingDeclarationName")

package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.*

internal enum class TooltipLocation {
    BOTTOM,
    RIGHT,
}

internal fun HTMLTag.tooltip(title: String, location: TooltipLocation = TooltipLocation.BOTTOM) {
    dataBsToggle("tooltip")
    attributes["data-bs-placement"] = location.name.lowercase()
    attributes["title"] = title
}

internal fun HTMLTag.modalOpener(modalId: String) {
    dataBsToggle("modal")
    dataBsTarget("#$modalId")
}

internal fun HTMLTag.dropdownToggler() {
    dataBsToggle("dropdown")
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

internal fun HTMLTag.dataBsTarget(target: String) {
    attributes["data-bs-target"] = target
}

internal fun HTMLTag.enableMasonry() {
    attributes["data-masonry"] = "{\"percentPosition\": true }"
}
