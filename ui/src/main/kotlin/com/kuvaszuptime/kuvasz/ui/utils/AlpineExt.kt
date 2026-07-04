package com.kuvaszuptime.kuvasz.ui.utils

import com.kuvaszuptime.kuvasz.ui.*
import kotlinx.html.*

internal fun HTMLTag.xBindDisabled(value: String) {
    attributes["x-bind:disabled"] = value
}

internal fun HTMLTag.xBindClass(value: String) {
    attributes["x-bind:class"] = value
}

internal fun HTMLTag.xBindErrorClass(errorProp: String) {
    xBindClass("errors.$errorProp ? '${CSSClass.IS_INVALID.className}' : ''")
}

internal fun HTMLTag.xBindSelected(value: String) {
    attributes["x-bind:selected"] = value
}

internal fun HTMLTag.xBindKey(value: String) {
    attributes["x-bind:key"] = value
}

internal fun HTMLTag.xBindSrc(value: String) {
    attributes["x-bind:src"] = value
}

internal fun HTMLTag.xBindValue(value: String) {
    attributes["x-bind:value"] = value
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

internal fun HTMLTag.xOnBlur(action: String) {
    xOn("blur", action)
}

internal fun HTMLTag.xOnChange(action: String) {
    xOn("change", action)
}

internal fun HTMLTag.xOnLoad(action: String) {
    xOn("load", action)
}

internal fun HTMLTag.xOnError(action: String) {
    xOn("error", action)
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

internal fun HTMLTag.xInitNextTick(action: String) {
    attributes["x-init"] = "\$nextTick(() => $action)"
}

internal fun HTMLTag.xFor(loopDef: String) {
    attributes["x-for"] = loopDef
}

internal fun HTMLTag.xShow(value: String) {
    attributes["x-show"] = value
}
