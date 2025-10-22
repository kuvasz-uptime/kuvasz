package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.pushMonitorCreateUpdateModal(
    modalId: String,
    monitor: PushMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val serializedMonitor: String? = monitor?.let { objectMapper.writeValueAsString(it) }
    val serializedErrorMessages = objectMapper.writeValueAsString(
        mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "nameOrClientSecretAlreadyExists" to Messages.errorNameOrClientSecretAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "heartbeatIntervalInvalid" to Messages.errorHeartbeatIntervalInvalid(),
            "gracePeriodInvalid" to Messages.errorGracePeriodInvalid(),
            "clientSecretInvalid" to Messages.errorClientSecretInvalid(),
        )
    )
    val modalClosedEvent = "push-monitor-upsert-modal-closed"
    val isReadOnlyMode = globals.editabilityState.arePushMonitorsReadOnly()
    val isMonitorNameReadOnly = monitor?.statusPages?.isNotEmpty() == true &&
        globals.editabilityState.areStatusPagesReadOnly()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertPushMonitorForm(
                |$serializedMonitor, 
                |$serializedErrorMessages, 
                |${globals.enabledIntegrations.count { it.value.global }})
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
                        if (monitor == null) {
                            +Messages.createNewPushMonitor()
                        } else if (isReadOnlyMode) {
                            +Messages.configurationOf(monitor.name)
                        } else {
                            +Messages.updateMonitor(monitor.name)
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
                    // Heartbeat Interval
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "heartbeatInterval",
                            label = Messages.heartbeatIntervalLabel(),
                            description = Messages.heartbeatIntervalDescription(),
                            placeholder = null,
                            required = true,
                            onInput = "validateHeartbeatInterval()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Grace period
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "gracePeriod",
                            label = Messages.gracePeriodLabel(),
                            description = Messages.gracePeriodDescription(),
                            placeholder = null,
                            required = true,
                            onInput = "validateGracePeriod()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Client secret
                    div {
                        val propName = "clientSecret"
                        classes(MB_3)
                        formLabel(
                            label = Messages.clientSecretLabel(),
                            description = Messages.clientSecretDescription(),
                            required = true,
                            inputName = "$propName-input",
                        )
                        div {
                            classes(ROW)
                            div {
                                classes(COL)
                                input {
                                    type = InputType.text
                                    classes(FORM_CONTROL)
                                    autoComplete = "off"
                                    xModel(propName)
                                    xBindErrorClass(propName)
                                    xOnInput("validateClientSecret()")
                                    xBindDisabled("$isReadOnlyMode")
                                }
                                templateTag {
                                    xIf("errors.$propName")
                                    div {
                                        classes(INVALID_FEEDBACK)
                                        xText("errors.$propName")
                                    }
                                }
                            }
                            div {
                                classes(COL_AUTO)
                                compactIconButton(Icon.REFRESH) {
                                    xBindDisabled("$isReadOnlyMode")
                                    xOnClick("generateNewClientSecret()")
                                }
                            }
                            div {
                                classes(COL_AUTO)
                                compactIconButton(Icon.CLIPBOARD) {
                                    xOnClick("copyClientSecretToClipboard()")
                                }
                            }
                        }
                    }
                    div {
                        classes(MB_3)
                        div {
                            classes(ALERT, ALERT_INFO)
                            role = "alert"
                            div {
                                classes(ALERT_ICON)
                                icon(Icon.CIRCLE_INFO)
                            }
                            div {
                                h4 {
                                    classes(ALERT_HEADING)
                                    +Messages.heartbeatSignalInfoTitle()
                                }
                                div {
                                    classes(ALERT_DESCRIPTION)
                                    unsafeText(Messages.clientSecretUsageDescription)
                                }
                            }
                        }
                    }

                    // Accordion for all the specific settings
                    val settingsAccordionId = "push-monitor-settings-accordion"
                    accordion(id = settingsAccordionId) {
                        // Integration Settings
                        integrationsAccordionItem(
                            elementId = "push-monitor-integration-settings",
                            parentAccordionId = settingsAccordionId,
                            configuredIntegrationsByType = globals.configuredIntegrationsByType,
                            isReadOnlyMode = isReadOnlyMode,
                        )
                    }
                }
                // Modal footer
                div {
                    classes(MODAL_FOOTER)
                    a(href = "#") {
                        classes(BTN, BTN_LINK, LINK_SECONDARY)
                        modalCloser()
                        if (isReadOnlyMode) {
                            +Messages.close()
                        } else {
                            +Messages.cancel()
                        }
                    }
                    if (!isReadOnlyMode) {
                        button {
                            classes(BTN, BTN_PRIMARY, MS_AUTO)
                            xBindDisabled("hasNonNullValue(errors) || isRequestLoading")
                            xOnClick("submitForm()")
                            icon(Icon.FLOPPY)
                            +Messages.save()
                        }
                    }
                }
            }
        }
    }
    handleFormResetOnModalClose(modalId = modalId, eventName = modalClosedEvent)
}
