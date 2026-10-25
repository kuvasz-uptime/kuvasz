package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.ui.CSSClass
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.formLabel(
    label: String,
    required: Boolean = false,
    inputName: String? = null,
    description: String? = null,
    descriptionShownIf: String? = null,
) {
    label {
        val labelClasses = mutableSetOf(FORM_LABEL).addIf(required, REQUIRED)
        classes(labelClasses)
        inputName?.let { htmlFor = it }
        if (required) required()
        +label
        if (!description.isNullOrEmpty()) {
            span {
                classes(MS_2)
                descriptionShownIf?.let { xShow(it) }
                tooltip(
                    title = description,
                    location = TooltipLocation.RIGHT
                )
                icon(Icon.INFO_CIRCLE)
            }
        }
    }
}

/**
 * A consumer that keeps the ids of the form fields rendered through it unique, when the same form is rendered for
 * multiple entity types on the same page, e.g. the create modals of all the monitor types on the dashboard. Every tag
 * inherits the consumer of its parent, so [idPrefix] covers every field of the subtree, see [fieldId].
 */
internal class FieldIdPrefixingConsumer(
    downstream: TagConsumer<Any?>,
    val idPrefix: String,
) : TagConsumer<Any?> by downstream

/** Renders a DIV whose whole subtree prefixes the ids of its form fields with [idPrefix]. */
internal fun FlowContent.fieldIdPrefixedDiv(idPrefix: String, block: DIV.() -> Unit) =
    DIV(emptyMap(), FieldIdPrefixingConsumer(consumer, idPrefix)).visit(block)

/** The id of a form field, prefixed if it's rendered in a [fieldIdPrefixedDiv]. */
internal fun FlowContent.fieldId(propName: String, suffix: String): String =
    listOfNotNull((consumer as? FieldIdPrefixingConsumer)?.idPrefix, propName, suffix).joinToString("-")

internal fun FlowContent.validatedInput(
    propName: String,
    label: String?,
    description: String? = null,
    placeholder: String? = propName,
    required: Boolean = false,
    onInput: String? = null,
    onBlur: String? = null,
    disabledIf: String? = null,
    isNumber: Boolean = false,
    numberRange: NumberRange? = null,
    dataListItems: Set<String> = emptySet(),
    smallControl: Boolean = false,
    descriptionShownIf: String? = null,
) {
    val inputName = fieldId(propName, "input")
    val dataListId = fieldId(propName, "datalist")
    if (!label.isNullOrEmpty()) {
        formLabel(
            label = label,
            required = required,
            inputName = inputName,
            description = description,
            descriptionShownIf = descriptionShownIf,
        )
    }
    val renderInput: FlowContent.() -> Unit = {
        input(type = InputType.text) {
            val classes = mutableSetOf(FORM_CONTROL).addIf(smallControl, FORM_CONTROL_SM)
            classes(classes)
            id = inputName
            name = inputName
            placeholder?.let { this.placeholder = it }
            xBindErrorClass(propName)
            onInput?.let { xOnInput(it) }
            onBlur?.let { xOnBlur(it) }
            disabledIf?.let { xBindDisabled(it) }
            if (isNumber) xModelNumber(propName) else xModel(propName)
            if (dataListItems.isNotEmpty()) list = dataListId
        }
    }
    if (numberRange != null) {
        numberInputWrapper(numberRange, small = smallControl, disabledIf = disabledIf) {
            renderInput()
        }
    } else {
        renderInput()
    }
    if (dataListItems.isNotEmpty()) {
        dataList {
            id = dataListId
            dataListItems.forEach { item ->
                option {
                    value = item
                }
            }
        }
    }
    templateTag {
        xIf("errors.$propName")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.$propName")
        }
    }
}

/** A row of form fields, which stack unless the screen is wide enough for the modal to be, see [formCol]. */
internal fun FlowContent.formRow(block: FlowContent.() -> Unit) {
    div {
        classes(ROW)
        block()
    }
}

/**
 * A field of a [formRow] with the given width on the large screens. The modals are only wide (800px) from there,
 * below it they're 500px, which is too narrow for the fields to share a row.
 */
internal fun FlowContent.formCol(width: CSSClass, block: FlowContent.() -> Unit) {
    div {
        classes(width, MB_3)
        block()
    }
}

/** The bounds and the step of a Tabler number input. A missing [max] means that the field has no upper limit. */
internal data class NumberRange(val min: Long, val max: Long? = null, val step: Long = 1)

/**
 * Wraps the input rendered by [block] in a Tabler number input with the increment and decrement buttons. It is
 * initialized by initNumberInput() in kuvasz.js, not by Tabler's data API, as that one would attach its listeners
 * before the ones of the former.
 */
internal fun FlowContent.numberInputWrapper(
    range: NumberRange,
    small: Boolean = false,
    disabledIf: String? = null,
    block: FlowContent.() -> Unit,
) {
    div {
        classes(mutableSetOf(NUMBER_INPUT).addIf(small, NUMBER_INPUT_SM))
        attributes["data-tblr-min"] = range.min.toString()
        range.max?.let { attributes["data-tblr-max"] = it.toString() }
        attributes["data-tblr-step"] = range.step.toString()
        xInit("initNumberInput(\$el)")
        numberInputButton("decrement", Messages.numberInputDecrease(), disabledIf)
        block()
        numberInputButton("increment", Messages.numberInputIncrease(), disabledIf)
    }
}

private fun FlowContent.numberInputButton(action: String, label: String, disabledIf: String?) {
    button(type = ButtonType.button) {
        classes(setOf(BTN, BTN_ICON))
        attributes["data-tblr-number-input-action"] = action
        attributes["aria-label"] = label
        disabledIf?.let { xBindDisabled(it) }
    }
}

internal fun FlowContent.validatedTextArea(
    propName: String,
    label: String?,
    description: String? = null,
    placeholder: String? = null,
    required: Boolean = false,
    onInput: String? = null,
    disabledIf: String? = null,
) {
    val inputName = fieldId(propName, "input")
    if (!label.isNullOrEmpty()) {
        formLabel(
            label = label,
            required = required,
            inputName = inputName,
            description = description
        )
    }
    textArea(rows = "4") {
        classes(FORM_CONTROL)
        id = inputName
        name = inputName
        placeholder?.let { this.placeholder = it }
        xModel(propName)
        xBindErrorClass(propName)
        onInput?.let { xOnInput(it) }
        disabledIf?.let { xBindDisabled(it) }
    }
    templateTag {
        xIf("errors.$propName")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.$propName")
        }
    }
}

/**
 * The input of a dateTimePickerField (see kuvasz.js), which has to be spread into the x-data of the form. The picked
 * value is validated by the form, which reports its errors under [errorProp].
 */
internal fun FlowContent.dateTimePickerInput(
    inputName: String,
    errorProp: String,
    label: String,
    description: String? = null,
    required: Boolean = false,
    isReadOnly: Boolean = false,
) {
    formLabel(
        label = label,
        description = description,
        inputName = inputName,
        required = required,
    )
    div {
        classes(INPUT_ICON)
        span {
            classes(INPUT_ICON_ADDON)
            icon(Icon.CALENDAR_EVENT)
        }
        input(type = InputType.text) {
            classes(FORM_CONTROL)
            id = inputName
            name = inputName
            placeholder = "YYYY-MM-DD HH:mm[:ss]"
            autoComplete = "off"
            attributes["data-bs-toggle"] = "datepicker"
            xInit("initDateTimePicker(\$el)")
            xBindValue("formatDateTimeValue(pickedDate, pickedTime)")
            xBindErrorClass(errorProp)
            xOnChange("onDateTimeTyped(\$event.target.value)")
            if (isReadOnly) disabled = true
        }
    }
    templateTag {
        xIf("errors.$errorProp")
        div {
            // The input isn't a sibling of the feedback, so it has to be displayed explicitly
            classes(INVALID_FEEDBACK, D_BLOCK)
            xText("errors.$errorProp")
        }
    }
}

internal fun FlowContent.toggleSwitch(
    propName: String,
    label: String,
    description: String? = null,
    isDisabled: Boolean = false,
    disabledIf: String? = null,
) {
    label {
        classes(FORM_CHECK, FORM_SWITCH)
        input(type = InputType.checkBox, name = propName) {
            classes(FORM_CHECK_INPUT)
            xModel(propName)
            if (isDisabled) disabled = true
            disabledIf?.let { xBindDisabled(it) }
        }
        span {
            classes(FORM_CHECK_LABEL)
            +label
            if (!description.isNullOrEmpty()) {
                span {
                    classes(MS_2)
                    tooltip(
                        title = description,
                        location = TooltipLocation.RIGHT
                    )
                    icon(Icon.INFO_CIRCLE)
                }
            }
        }
    }
}

internal fun FlowContent.integrationsAccordionItem(
    elementId: String,
    parentAccordionId: String,
    configuredIntegrationsByType: Map<IntegrationType, Set<IntegrationConfig>>,
    isReadOnlyMode: Boolean,
    renderGlobalIcon: Boolean = true,
) =
    // Integration Settings
    accordionItem(
        id = elementId,
        parentId = parentAccordionId,
        title = Messages.integrationsLabel(),
        titleIcon = Icon.PLUG,
        additionalTitleContent = {
            templateTag {
                xIf("integrations.length + globalIntegrationCount === 0")
                span {
                    classes(TEXT_RED)
                    icon(Icon.CIRCLE_EXCLAMATION_FILLED)
                }
            }
            templateTag {
                xIf("integrations.length + globalIntegrationCount > 0")
                span {
                    classes(TEXT_GREEN)
                    icon(Icon.CIRCLE_CHECK_FILLED)
                }
            }
        },
    ) {
        val enabledIntegrationsByType = configuredIntegrationsByType.toSortedMap()
        div {
            classes(MB_3)
            if (enabledIntegrationsByType.isEmpty()) {
                p {
                    classes(TEXT_MUTED)
                    +Messages.noIntegrationsAvailable()
                }
            }
            enabledIntegrationsByType.forEach { (type, integrations) ->
                // Render each integration type with its integrations
                div {
                    classes(FORM_LABEL, MT_2)
                    icon(type.icon)
                    span {
                        classes(MS_2)
                        +type.displayName
                    }
                }
                div {
                    // Render each integration as a checkbox
                    integrations.sortedBy { it.name.lowercase() }.forEach { integration ->
                        label {
                            classes(FORM_CHECK, FORM_CHECK_INLINE)
                            testId("integration-checkbox")
                            input(type = InputType.checkBox) {
                                value = integration.id.toString()
                                classes(FORM_CHECK_INPUT)
                                xModel("integrations")
                                if (isReadOnlyMode) disabled = true
                            }
                            span {
                                classes(FORM_CHECK_LABEL)
                                if (integration.global && renderGlobalIcon) {
                                    span {
                                        classes(ME_2, TEXT_GREEN)
                                        tooltip(
                                            title = Messages.globalIntegrationInfo(),
                                            location = TooltipLocation.RIGHT
                                        )
                                        icon(Icon.WORLD)
                                    }
                                }
                                +integration.name
                                if (!integration.enabled) {
                                    span {
                                        classes(MS_2, TEXT_YELLOW)
                                        tooltip(
                                            title = Messages.disabledIntegrationInfo(),
                                            location = TooltipLocation.RIGHT
                                        )
                                        icon(Icon.ALERT_TRIANGLE)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
