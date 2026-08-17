package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The modal that creates or updates a monitor. Every type opens with the same name field and closes with the same
 * integration settings and footer, so a type only has to render [fields], the part describing what it checks.
 *
 * [extraFormArgs] are appended to the arguments of the Alpine.js form component, after the monitor and the error
 * messages and before the number of global integrations, for the types whose form needs more context.
 */
internal fun FlowContent.monitorUpsertModal(
    modalId: String,
    typeUiConfig: MonitorTypeUiConfig,
    monitor: MonitorDetailsDto?,
    globals: AppGlobals,
    createTitle: String,
    errorMessages: Map<String, String>,
    extraFormArgs: List<String> = emptyList(),
    // The accordion items of the settings a type has beyond the integrations every monitor shares
    extraSettings: FlowContent.(isReadOnlyMode: Boolean, accordionId: String) -> Unit = { _, _ -> },
    fields: FlowContent.(isReadOnlyMode: Boolean) -> Unit,
) {
    val serializedMonitor: String? = monitor?.asJsonString()
    val modalClosedEvent = "${typeUiConfig.slug}-monitor-upsert-modal-closed"
    val isReadOnlyMode = globals.editabilityState.areMonitorsReadOnly(typeUiConfig.type)
    val isMonitorNameReadOnly = monitor?.statusPages?.isNotEmpty() == true &&
        globals.editabilityState.areStatusPagesReadOnly()
    val formArgs = listOf(serializedMonitor.toString(), errorMessages.asJsonString()) +
        extraFormArgs +
        globals.enabledIntegrations.count { it.value.global }.toString()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            formArgs.joinToString(
                separator = ",\n",
                prefix = "${typeUiConfig.upsertFormComponent}(\n",
                postfix = ")"
            )
        )
        attributes["@$modalClosedEvent.window"] = "resetState()"
        attributes["@clone-monitor.window"] = "cloneFrom(\$event.detail.id, \$event.detail.name)"
        tabIndex = "-1"
        role = "dialog"

        div {
            classes(MODAL_DIALOG, MODAL_LG, MODAL_DIALOG_CENTERED)
            role = "document"

            div {
                classes(MODAL_CONTENT, POSITION_RELATIVE)
                // Modal header
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        when {
                            monitor == null -> +createTitle
                            isReadOnlyMode -> +Messages.configurationOf(monitor.name)
                            else -> +Messages.updateMonitor(monitor.name)
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
                        // Showing the tooltip only if the name is read-only but the rest of the form is editable
                        val tooltip = if (isMonitorNameReadOnly && !isReadOnlyMode) {
                            Messages.monitorNameReadOnlyTooltip()
                        } else {
                            null
                        }
                        validatedInput(
                            propName = "name",
                            label = Messages.monitorNameLabel(),
                            placeholder = Messages.monitorNamePlaceholder(),
                            description = tooltip,
                            required = true,
                            onInput = "validateName()",
                            disabledIf = "$isReadOnlyMode || $isMonitorNameReadOnly",
                        )
                    }
                    // Category
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "category",
                            label = Messages.monitorCategoryLabel(),
                            placeholder = Messages.monitorCategoryPlaceholder(),
                            description = Messages.monitorCategoryDescription(),
                            required = false,
                            onInput = "validateCategory()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    fields(isReadOnlyMode)

                    // Accordion for all the specific settings
                    val settingsAccordionId = "${typeUiConfig.slug}-monitor-settings-accordion"
                    accordion(id = settingsAccordionId) {
                        extraSettings(isReadOnlyMode, settingsAccordionId)
                        // Integration Settings
                        integrationsAccordionItem(
                            elementId = "${typeUiConfig.slug}-monitor-integration-settings",
                            parentAccordionId = settingsAccordionId,
                            configuredIntegrationsByType = globals.configuredIntegrationsByType,
                            isReadOnlyMode = isReadOnlyMode,
                        )
                    }
                }
                // Modal footer
                upsertModalFooter(
                    isReadOnlyMode,
                    xSaveDisabledIf = "hasNonNullValue(errors) || isRequestLoading || isCloning",
                    xOnSaveClicked = "submitForm()",
                )
                cloningOverlay()
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = modalClosedEvent)
}
