package com.kuvaszuptime.kuvasz.ui.fragments

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.selectGroup(xModelName: String, readOnly: Boolean, values: List<ValueAndLabel>) {
    div {
        classes(FORM_SELECTGROUP, MB_2)
        values.forEach { option ->
            label {
                classes(FORM_SELECTGROUP_ITEM)
                input(type = InputType.radio, name = xModelName) {
                    classes(FORM_SELECTGROUP_INPUT)
                    this.value = option.value
                    xModel(xModelName)
                    if (readOnly) disabled = true
                }
                span {
                    classes(FORM_SELECTGROUP_LABEL)
                    +option.label
                }
            }
        }
    }
}

data class ValueAndLabel(
    val value: String,
    val label: String,
)
