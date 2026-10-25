package com.kuvaszuptime.kuvasz.ui.components

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The badge in the header of the filter panel, counting the active filters of the list rendered into
 * [listElementId]. Every response of the list swaps it out-of-band, so it follows the changes of the filters.
 */
internal fun FlowContent.activeFilterCount(listElementId: String, count: Int, outOfBand: Boolean) {
    span {
        id = "$listElementId-active-filter-count"
        classes(mutableSetOf(BADGE, BG_BLUE_LT, MS_2).addIf(count == 0, D_NONE))
        testId("active-filter-count")
        if (outOfBand) hx { swapOob() }
        +"$count"
    }
}

/**
 * The filters of a paged list, in an accordion that is closed by default. The controls are bound to the state of the
 * listQuery Alpine.js component of the list (see kuvasz.js), and they live outside the list on purpose: the list
 * refreshes itself every few seconds, and a control living inside it would be torn out from under the cursor.
 */
internal fun FlowContent.listFilterPanel(listElementId: String, query: ListQuery<*, *>) {
    val accordionId = "$listElementId-filters"
    div {
        // Separates the filters from the header of the table right under them
        classes(PX_3, BORDER_BOTTOM)
        accordion(id = accordionId) {
            accordionItem(
                id = "$accordionId-panel",
                parentId = accordionId,
                title = Messages.filters(),
                titleIcon = Icon.FILTER,
                additionalTitleContent = { activeFilterCount(listElementId, query.activeFilterCount, outOfBand = false) },
            ) {
                div {
                    classes(ROW, G_3)
                    query.filters.all.forEach { filter ->
                        div {
                            classes(COL_12, COL_SM_6, COL_LG_3)
                            filterControl(filter)
                        }
                    }
                }
                button(type = ButtonType.button) {
                    classes(BTN, BTN_SM, MT_3)
                    testId("clear-filters-button")
                    xOnClick("clearFilters()")
                    icon(Icon.X)
                    +Messages.clearFilters()
                }
            }
        }
    }
}

private fun FlowContent.filterControl(filter: ListFilter) {
    val inputId = "list-filter-${filter.param}"
    // Accessed by index, so a parameter name never has to be a valid JS identifier
    val stateValue = "filters['${filter.param}']"
    when (filter) {
        is TextFilter -> {
            formLabel(filter.label, inputName = inputId)
            div {
                classes(INPUT_ICON)
                span {
                    classes(INPUT_ICON_ADDON)
                    icon(Icon.SEARCH)
                }
                input(type = InputType.search) {
                    classes(FORM_CONTROL)
                    id = inputId
                    placeholder = filter.placeholder
                    autoComplete = "off"
                    value = filter.value.orEmpty()
                    testId(inputId)
                    attributes["x-model.debounce.300ms"] = stateValue
                }
            }
        }

        is NumberFilter -> {
            formLabel(filter.label, inputName = inputId)
            numberInputWrapper(NumberRange(min = 1)) {
                input(type = InputType.text) {
                    classes(FORM_CONTROL)
                    id = inputId
                    autoComplete = "off"
                    value = filter.value?.toString().orEmpty()
                    testId(inputId)
                    attributes["x-model.debounce.300ms"] = stateValue
                }
            }
        }

        is ChoiceFilter<*> -> {
            formLabel(filter.label, inputName = inputId)
            choiceSelect(inputId, stateValue) {
                option {
                    value = NO_PARAMETER_OPTION
                    selected = filter.selected == null
                    +filter.anyLabel
                }
                filter.options.forEach { option ->
                    option {
                        value = option.queryValue.asJsonString()
                        selected = option == filter.selected
                        +option.label
                    }
                }
            }
        }

        is DefaultedChoiceFilter<*> -> {
            formLabel(filter.label, inputName = inputId)
            choiceSelect(inputId, stateValue) {
                filter.options.forEach { option ->
                    option {
                        // The default leaves the parameter out
                        value = if (option == filter.default) NO_PARAMETER_OPTION else option.queryValue.asJsonString()
                        selected = option == filter.selected
                        +option.label
                    }
                }
            }
        }

        is MultiChoiceFilter<*> -> {
            formLabel(filter.label)
            div {
                classes(FORM_SELECTGROUP, FORM_SELECTGROUP_PILLS)
                testId(inputId)
                filter.options.forEach { option ->
                    label {
                        classes(FORM_SELECTGROUP_ITEM)
                        input(type = InputType.checkBox) {
                            classes(FORM_SELECTGROUP_INPUT)
                            value = option.queryValue
                            checked = option in filter.selected
                            xModel(stateValue)
                        }
                        span {
                            classes(FORM_SELECTGROUP_LABEL)
                            +option.label
                        }
                    }
                }
            }
        }
    }
}

// The value of the option of a select that leaves the parameter out, see choiceSelect()
private const val NO_PARAMETER_OPTION = "null"

/**
 * The select of a choice filter. The values of its options are JSON, so leaving the parameter out (null) and an empty
 * parameter, like the one of the monitors without a category, can be told apart.
 */
private fun FlowContent.choiceSelect(inputId: String, stateValue: String, options: SELECT.() -> Unit) {
    select {
        classes(FORM_SELECT)
        id = inputId
        testId(inputId)
        xBindValue("JSON.stringify($stateValue)")
        xOnChange("$stateValue = JSON.parse(\$event.target.value)")
        options()
    }
}
