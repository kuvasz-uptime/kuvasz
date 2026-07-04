package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.icmpMonitorCreateUpdateModal(
    modalId: String,
    monitor: IcmpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val serializedMonitor: String? = monitor?.let { objectMapper.writeValueAsString(it) }
    val serializedErrorMessages = objectMapper.writeValueAsString(
        mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "hostRequired" to Messages.errorHostRequired(),
            "packetCountInvalid" to Messages.errorPacketCountInvalid(),
            "timeoutSecondsInvalid" to Messages.errorTimeoutSecondsInvalid(),
            "packetLossThresholdInvalid" to Messages.errorPacketLossThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        )
    )
    val modalClosedEvent = "icmp-monitor-upsert-modal-closed"
    val isReadOnlyMode = globals.editabilityState.areIcmpMonitorsReadOnly()
    val isMonitorNameReadOnly = monitor?.statusPages?.isNotEmpty() == true &&
        globals.editabilityState.areStatusPagesReadOnly()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertIcmpMonitorForm(
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
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        if (monitor == null) {
                            +Messages.createNewIcmpMonitor()
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
                div {
                    classes(MODAL_BODY, PB_0)
                    // Name
                    div {
                        classes(MB_3)
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
                    // Host
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "host",
                            label = Messages.hostLabel(),
                            placeholder = Messages.hostPlaceholder(),
                            description = null,
                            required = true,
                            onInput = "validateHost()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Uptime check interval
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "uptimeCheckInterval",
                            label = Messages.uptimeCheckIntervalLabel(),
                            placeholder = null,
                            description = null,
                            required = true,
                            onInput = "validateUptimeCheckInterval()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Packet count
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "packetCount",
                            label = Messages.packetCountLabel(),
                            placeholder = null,
                            description = Messages.packetCountDescription(),
                            required = true,
                            onInput = "validatePacketCount()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Timeout seconds
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "timeoutSeconds",
                            label = Messages.timeoutSecondsLabel(),
                            placeholder = null,
                            description = Messages.timeoutSecondsDescription(),
                            required = true,
                            onInput = "validateTimeoutSeconds()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Packet loss threshold
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "packetLossThreshold",
                            label = Messages.packetLossThresholdLabel(),
                            placeholder = null,
                            description = Messages.packetLossThresholdDescription(),
                            required = true,
                            onInput = "validatePacketLossThreshold()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Failure count threshold
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "failureCountThreshold",
                            label = Messages.failureCountThresholdLabel(),
                            description = Messages.failureCountThresholdDescription(),
                            placeholder = null,
                            required = true,
                            onInput = "validateFailureCountThreshold()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Metrics History
                    div {
                        classes(MB_4)
                        toggleSwitch(
                            propName = "metricsHistoryEnabled",
                            label = Messages.metricsHistorySwitchLabel(),
                            description = Messages.metricsHistorySwitchDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }

                    val settingsAccordionId = "icmp-monitor-settings-accordion"
                    accordion(id = settingsAccordionId) {
                        integrationsAccordionItem(
                            elementId = "icmp-monitor-integration-settings",
                            parentAccordionId = settingsAccordionId,
                            configuredIntegrationsByType = globals.configuredIntegrationsByType,
                            isReadOnlyMode = isReadOnlyMode,
                        )
                    }
                }
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
