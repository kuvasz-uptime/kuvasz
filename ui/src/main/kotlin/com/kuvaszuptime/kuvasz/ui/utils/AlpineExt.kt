package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.*

internal fun HTMLTag.xBindDisabled(value: String) {
    attributes["x-bind:disabled"] = value
}

internal fun HTMLTag.xBindClass(value: String) {
    attributes["x-bind:class"] = value
}

internal fun HTMLTag.xData(value: String) {
    attributes["x-data"] = value
}

internal fun HTMLTag.xOn(event: String, action: String) {
    attributes["x-on:$event"] = action
}

internal fun HTMLTag.xOnClick(action: String) {
    xOn("click", action)
}

internal fun HTMLTag.xOnInput(action: String) {
    xOn("input", action)
}

internal fun HTMLTag.xModel(value: String) {
    attributes["x-model"] = value
}

internal fun HTMLTag.xModelNumber(value: String) {
    attributes["x-model.number"] = value
}

internal fun HTMLTag.xText(value: String) {
    attributes["x-text"] = value
}

internal fun HTMLTag.xIf(value: String) {
    attributes["x-if"] = value
}
