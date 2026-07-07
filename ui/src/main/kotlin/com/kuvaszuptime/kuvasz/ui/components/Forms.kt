package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.formLabel(
    label: String,
    required: Boolean = false,
    inputName: String? = null,
    description: String? = null
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
                tooltip(
                    title = description,
                    location = TooltipLocation.RIGHT
                )
                icon(Icon.INFO_CIRCLE)
            }
        }
    }
}

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
    dataListItems: Set<String> = emptySet(),
    smallControl: Boolean = false,
) {
    val inputName = "$propName-input"
    val dataListId = "$propName-datalist"
    if (!label.isNullOrEmpty()) {
        formLabel(
            label = label,
            required = required,
            inputName = inputName,
            description = description
        )
    }
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

internal fun FlowContent.validatedTextArea(
    propName: String,
    label: String?,
    description: String? = null,
    placeholder: String? = null,
    required: Boolean = false,
    onInput: String? = null,
    disabledIf: String? = null,
) {
    val inputName = "$propName-input"
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
                        +type.identifier.capitalize()
                    }
                }
                div {
                    // Render each integration as a checkbox
                    integrations.sortedBy { it.name }.forEach { integration ->
                        label {
                            classes(FORM_CHECK, FORM_CHECK_INLINE)
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
