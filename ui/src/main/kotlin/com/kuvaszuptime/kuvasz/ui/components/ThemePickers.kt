package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.theme.ThemeBase
import com.kuvaszuptime.kuvasz.models.theme.ThemeOption
import com.kuvaszuptime.kuvasz.models.theme.ThemePrimary
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

private const val BASE_OPTION = "base"
private const val PRIMARY_OPTION = "primary"

/**
 * Binds a theme picker to its Alpine component
 *
 * @param xModelName the model holding the picked option, also naming the radio group
 * @param radioValue the value the model gets when an option is picked
 **/
internal class ThemePickerBinding(
    val xModelName: String,
    val radioValue: (ThemeOption) -> String,
    val xOnChange: String? = null,
    val isDisabled: Boolean = false,
)

/**
 * The picker of the gray palettes, which only differ in their tint, so they're picked by their names, with a light and
 * a dark shade of each
 **/
internal fun FlowContent.themeBasePicker(binding: ThemePickerBinding) {
    themeOptionGroup(BASE_OPTION, Messages.themeBaseLabel()) {
        div {
            classes(FORM_SELECTGROUP, FORM_SELECTGROUP_PILLS)
            ThemeBase.entries.forEach { themeBase ->
                label {
                    classes(FORM_SELECTGROUP_ITEM)
                    themeOptionRadio(binding, themeBase, FORM_SELECTGROUP_INPUT)
                    span {
                        classes(FORM_SELECTGROUP_LABEL, D_FLEX, ALIGN_ITEMS_CENTER)
                        themeSwatch(BASE_OPTION, themeBase, THEME_SWATCH_BASE)
                        span {
                            classes(MS_2)
                            +themeBase.label
                        }
                    }
                }
            }
        }
    }
}

/**
 * The picker of the accent colors
 **/
internal fun FlowContent.themePrimaryPicker(binding: ThemePickerBinding) {
    themeOptionGroup(PRIMARY_OPTION, Messages.themePrimaryLabel()) {
        div {
            classes(ROW, G_2)
            ThemePrimary.entries.forEach { themePrimary ->
                div {
                    classes(COL_AUTO)
                    label {
                        classes(FORM_COLORINPUT)
                        title = themePrimary.label
                        themeOptionRadio(binding, themePrimary, FORM_COLORINPUT_INPUT)
                        themeSwatch(PRIMARY_OPTION, themePrimary, FORM_COLORINPUT_COLOR, THEME_SWATCH_PRIMARY)
                    }
                }
            }
        }
    }
}

private fun FlowContent.themeOptionGroup(option: String, label: String, content: FlowContent.() -> Unit) {
    div {
        classes(MB_3)
        testId("theme-$option-picker")
        div {
            classes(FORM_LABEL)
            +label
        }
        content()
    }
}

private fun FlowContent.themeOptionRadio(binding: ThemePickerBinding, themeOption: ThemeOption, inputClass: CSSClass) {
    input(type = InputType.radio, name = binding.xModelName) {
        classes(inputClass)
        value = binding.radioValue(themeOption)
        ariaLabel(themeOption.label)
        xModel(binding.xModelName)
        binding.xOnChange?.let { xOnChange(it) }
        if (binding.isDisabled) disabled = true
    }
}

// Shows the colors of the option by being scoped to the theme attribute it stands for
private fun FlowContent.themeSwatch(option: String, themeOption: ThemeOption, vararg swatchClasses: CSSClass) {
    span {
        classes(*swatchClasses)
        attributes["data-bs-theme-$option"] = themeOption.value
    }
}
