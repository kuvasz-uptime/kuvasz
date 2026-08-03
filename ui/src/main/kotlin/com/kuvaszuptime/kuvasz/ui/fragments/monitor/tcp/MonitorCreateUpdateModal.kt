package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.tcpMonitorCreateUpdateModal(
    modalId: String,
    monitor: TcpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewTcpMonitor(),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "hostRequired" to Messages.errorHostRequired(),
            "portInvalid" to Messages.errorPortInvalid(),
            "timeoutMsInvalid" to Messages.errorTimeoutMsInvalid(),
            "latencyThresholdInvalid" to Messages.errorLatencyThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        ),
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
    }
}
