package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import java.time.Duration

internal fun FlowContent.maintenanceWindowCreateUpdateModal(
    modalId: String,
    maintenanceWindow: MaintenanceWindowDetailsDto?,
    globals: AppGlobals,
) {
    val serializedWindow: String? = maintenanceWindow?.let { objectMapper.writeValueAsString(it) }
    val serializedErrorMessages = objectMapper.writeValueAsString(
        mapOf(
            "nameRequired" to Messages.errorMaintenanceWindowNameRequired(),
            "nameAlreadyExists" to Messages.errorMaintenanceWindowNameAlreadyExists(),
            "cronRequired" to Messages.errorMaintenanceWindowCronRequired(),
            "cronInvalid" to Messages.errorMaintenanceWindowCronInvalid(),
            "startRequired" to Messages.errorMaintenanceWindowStartRequired(),
            "durationRequired" to Messages.errorMaintenanceWindowDurationRequired(),
            "durationInvalid" to Messages.errorMaintenanceWindowDurationInvalid(),
        )
    )
    val configuredMonitors = globals.configuredMonitors()
    val serializedMonitors = objectMapper.writeValueAsString(configuredMonitors)
    val modalClosedEvent = "maintenance-window-upsert-modal-closed"
    val monitorsSelectId = "maintenance-window-monitors-select"
    val isReadOnlyMode = globals.editabilityState.areMaintenanceWindowsReadOnly()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertMaintenanceWindowForm(
                |$serializedWindow,
                |$serializedErrorMessages,
                |'$monitorsSelectId',
                |$serializedMonitors)
            """.trimMargin()
        )
        attributes["@$modalClosedEvent.window"] = "resetState()"
        tabIndex = "-1"
        role = "dialog"

        div {
            classes(MODAL_DIALOG, MODAL_LG, MODAL_DIALOG_CENTERED)
            role = "document"

            div {
                classes(MODAL_CONTENT)
                // Modal header
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        if (maintenanceWindow == null) {
                            +Messages.createNewMaintenanceWindow()
                        } else if (isReadOnlyMode) {
                            +Messages.configurationOf(maintenanceWindow.name)
                        } else {
                            +Messages.updateMaintenanceWindow(maintenanceWindow.name)
                        }
                    }
                    button(type = ButtonType.button) {
                        classes(BTN_CLOSE)
                        modalCloser()
                    }
                }
                // Modal body
                div {
                    classes(MODAL_BODY, PB_0)
                    // Name
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "name",
                            label = Messages.name(),
                            placeholder = Messages.maintenanceWindowNamePlaceholder(),
                            required = true,
                            onInput = "validateName()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Description
                    div {
                        classes(MB_3)
                        validatedTextArea(
                            propName = "description",
                            label = Messages.maintenanceWindowDescriptionLabel(),
                            placeholder = Messages.maintenanceWindowDescriptionPlaceholder(),
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Type selector
                    div {
                        classes(MB_3)
                        maintenanceWindowTypeSelector(isReadOnlyMode)
                    }
                    // Cron (only for cron windows)
                    div {
                        classes(MB_3)
                        xShow("type === '${MaintenanceWindowType.CRON.name}'")
                        validatedInput(
                            propName = "cron",
                            label = Messages.maintenanceWindowCronLabel(),
                            placeholder = Messages.maintenanceWindowCronPlaceholder(),
                            description = Messages.maintenanceWindowCronDescription(),
                            required = true,
                            onBlur = "validateCron()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Start (only for single windows)
                    div {
                        classes(MB_3)
                        xShow("type === '${MaintenanceWindowType.SINGLE.name}'")
                        maintenanceWindowStartInput(isReadOnlyMode)
                    }
                    // Duration (cron + single windows)
                    div {
                        classes(MB_3)
                        xShow("type !== '${MaintenanceWindowType.MANUAL.name}'")
                        validatedInput(
                            propName = "duration",
                            label = Messages.maintenanceWindowDurationLabel(),
                            placeholder = Messages.maintenanceWindowDurationPlaceholder(),
                            description = Messages.maintenanceWindowDurationDescription(),
                            required = true,
                            onInput = "validateDuration()",
                            disabledIf = "$isReadOnlyMode",
                        )
                        maintenanceWindowDurationPresets(isReadOnlyMode)
                    }
                    // Enabled
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "enabled",
                            label = Messages.maintenanceWindowEnabledLabel(),
                            description = Messages.maintenanceWindowEnabledDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }
                    // Global
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "global",
                            label = Messages.maintenanceWindowGlobalLabel(),
                            description = Messages.maintenanceWindowGlobalDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }
                    // Show on status pages
                    div {
                        classes(MB_4)
                        toggleSwitch(
                            propName = "showOnStatusPages",
                            label = Messages.maintenanceWindowShowOnStatusPagesLabel(),
                            description = Messages.maintenanceWindowShowOnStatusPagesDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }
                    // Monitors (hidden for global windows)
                    div {
                        classes(MB_3)
                        xShow("!global")
                        formLabel(
                            label = Messages.monitors(),
                            description = Messages.maintenanceWindowMonitorsDescription(),
                            inputName = monitorsSelectId,
                            required = false,
                        )
                        monitorSelector(
                            xModelName = "selectedMonitors",
                            monitorsSelectId = monitorsSelectId,
                            isReadOnly = isReadOnlyMode,
                        )
                    }
                    // Integrations
                    div {
                        classes(MB_3)
                        val accordionId = "maintenance-window-integrations-accordion"
                        accordion(id = accordionId) {
                            integrationsAccordionItem(
                                elementId = "maintenance-window-integration-settings",
                                parentAccordionId = accordionId,
                                configuredIntegrationsByType = globals.configuredIntegrationsByType,
                                isReadOnlyMode = isReadOnlyMode,
                                renderGlobalIcon = false,
                            )
                        }
                    }
                }
                // Modal footer
                upsertModalFooter(
                    isReadOnlyMode,
                    xSaveDisabledIf = "hasNonNullValue(errors) || isRequestLoading",
                    xOnSaveClicked = "submitForm()",
                )
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = modalClosedEvent)
}

private fun FlowContent.maintenanceWindowTypeSelector(isReadOnly: Boolean) {
    val elementId = "maintenance-window-type-select"
    formLabel(
        label = Messages.maintenanceWindowTypeLabel(),
        description = Messages.maintenanceWindowTypeDescription(),
        inputName = elementId,
        required = true,
    )
    div {
        id = elementId
        classes(FORM_SELECTGROUP)
        MaintenanceWindowType.entries.forEach { type ->
            label {
                classes(FORM_SELECTGROUP_ITEM)
                input(type = InputType.radio, name = "maintenance-window-type") {
                    classes(FORM_SELECTGROUP_INPUT)
                    value = type.name
                    xModel("type")
                    xOnChange("onTypeChange()")
                    if (isReadOnly) disabled = true
                }
                span {
                    classes(FORM_SELECTGROUP_LABEL)
                    icon(type.icon())
                    span {
                        classes(MS_1)
                        +type.label()
                    }
                }
            }
        }
    }
}

@Suppress("MagicNumber")
private fun FlowContent.maintenanceWindowDurationPresets(isReadOnly: Boolean) {
    val presets = listOf(
        Messages.minutesInterval(30) to Duration.ofMinutes(30).toString(),
        Messages.hourInterval(1) to Duration.ofHours(1).toString(),
        Messages.dayInterval(1) to Duration.ofDays(1).toString(),
    )
    div {
        classes(BTN_LIST, MT_2)
        presets.forEach { (label, isoValue) ->
            button(type = ButtonType.button) {
                classes(BTN, BTN_SM)
                xOnClick("setDuration('$isoValue')")
                if (isReadOnly) disabled = true
                +label
            }
        }
    }
}

private fun FlowContent.maintenanceWindowStartInput(isReadOnly: Boolean) {
    val inputName = "start-input"
    formLabel(
        label = Messages.maintenanceWindowStartLabel(),
        description = Messages.maintenanceWindowStartDescription(),
        inputName = inputName,
        required = true,
    )
    input(type = InputType.dateTimeLocal) {
        classes(FORM_CONTROL, NATIVE_DATETIME_INPUT)
        id = inputName
        name = inputName
        xModel("start")
        xBindErrorClass("start")
        xOnInput("validateStart()")
        // Open the native picker when the field is clicked anywhere, not only on the (hidden) calendar icon
        onClick = "try { this.showPicker() } catch (e) {}"
        if (isReadOnly) disabled = true
    }
    templateTag {
        xIf("errors.start")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.start")
        }
    }
}
