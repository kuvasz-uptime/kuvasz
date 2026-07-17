package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.tcpMonitorCreateUpdateModal(
    modalId: String,
    monitor: TcpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val serializedMonitor: String? = monitor?.asJsonString()
    val serializedErrorMessages = mapOf(
        "nameRequired" to Messages.errorNameRequired(),
        "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
        "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
        "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
        "hostRequired" to Messages.errorHostRequired(),
        "portInvalid" to Messages.errorPortInvalid(),
        "timeoutMsInvalid" to Messages.errorTimeoutMsInvalid(),
        "latencyThresholdInvalid" to Messages.errorLatencyThresholdInvalid(),
        "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
    ).asJsonString()
    val modalClosedEvent = "tcp-monitor-upsert-modal-closed"
    val isReadOnlyMode = globals.editabilityState.areTcpMonitorsReadOnly()
    val isMonitorNameReadOnly = monitor?.statusPages?.isNotEmpty() == true &&
        globals.editabilityState.areStatusPagesReadOnly()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertTcpMonitorForm(
                |$serializedMonitor,
                |$serializedErrorMessages,
                |${globals.enabledIntegrations.count { it.value.global }})
            """.trimMargin()
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
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        if (monitor == null) {
                            +Messages.createNewTcpMonitor()
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
                    // Port
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "port",
                            label = Messages.portLabel(),
                            placeholder = Messages.portPlaceholder(),
                            description = null,
                            required = true,
                            onInput = "validatePort()",
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
                    // Timeout (ms)
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "timeoutMs",
                            label = Messages.timeoutMsLabel(),
                            placeholder = null,
                            description = Messages.timeoutMsDescription(),
                            required = true,
                            onInput = "validateTimeoutMs()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Latency threshold (optional)
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "latencyThresholdMs",
                            label = Messages.latencyThresholdLabel(),
                            placeholder = null,
                            description = Messages.latencyThresholdDescription(),
                            required = false,
                            onInput = "validateLatencyThreshold()",
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

                    val settingsAccordionId = "tcp-monitor-settings-accordion"
                    accordion(id = settingsAccordionId) {
                        integrationsAccordionItem(
                            elementId = "tcp-monitor-integration-settings",
                            parentAccordionId = settingsAccordionId,
                            configuredIntegrationsByType = globals.configuredIntegrationsByType,
                            isReadOnlyMode = isReadOnlyMode,
                        )
                    }
                }
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
