package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.icmpMonitorCreateUpdateModal(
    modalId: String,
    monitor: IcmpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewIcmpMonitor(),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "categoryTooLong" to Messages.errorCategoryTooLong(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "hostRequired" to Messages.errorHostRequired(),
            "packetCountInvalid" to Messages.errorPacketCountInvalid(),
            "timeoutSecondsInvalid" to Messages.errorTimeoutSecondsInvalid(),
            "packetLossThresholdInvalid" to Messages.errorPacketLossThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        ),
        extraSettings = { isReadOnlyMode, settingsAccordionId ->
            accordionItem(
                id = "icmp-monitor-evaluation-settings",
                parentId = settingsAccordionId,
                title = Messages.evaluationSettingsLabel(),
                titleIcon = Icon.LIST_CHECK,
            ) {
                formRow {
                    formCol(COL_LG_6) {
                        validatedInput(
                            propName = "packetCount",
                            label = Messages.packetCountLabel(),
                            placeholder = null,
                            description = Messages.packetCountDescription(),
                            required = true,
                            onInput = "validatePacketCount()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(Validation.MIN_PACKET_COUNT, Validation.MAX_PACKET_COUNT),
                        )
                    }
                    formCol(COL_LG_6) {
                        validatedInput(
                            propName = "timeoutSeconds",
                            label = Messages.timeoutSecondsLabel(),
                            placeholder = null,
                            description = Messages.timeoutSecondsDescription(),
                            required = true,
                            onInput = "validateTimeoutSeconds()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(Validation.MIN_TIMEOUT_SECONDS, Validation.MAX_TIMEOUT_SECONDS),
                        )
                    }
                }
                formRow {
                    formCol(COL_LG_6) {
                        validatedInput(
                            propName = "packetLossThreshold",
                            label = Messages.packetLossThresholdLabel(),
                            placeholder = null,
                            description = Messages.packetLossThresholdDescription(),
                            required = true,
                            onInput = "validatePacketLossThreshold()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(
                                Validation.MIN_PACKET_LOSS_THRESHOLD,
                                Validation.MAX_PACKET_LOSS_THRESHOLD,
                            ),
                        )
                    }
                    formCol(COL_LG_6) {
                        validatedInput(
                            propName = "failureCountThreshold",
                            label = Messages.failureCountThresholdLabel(),
                            description = Messages.failureCountThresholdDescription(),
                            placeholder = null,
                            required = true,
                            onInput = "validateFailureCountThreshold()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(1),
                        )
                    }
                }
            }
        },
    ) { isReadOnlyMode ->
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
                numberRange = NumberRange(Validation.MIN_UPTIME_CHECK_INTERVAL, step = 10),
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
    }
}
