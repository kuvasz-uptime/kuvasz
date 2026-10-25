package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.tcpMonitorCreateUpdateModal(
    modalId: String,
    monitor: TcpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val proxySelectId = "tcp-monitor-proxy-select"
    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewTcpMonitor(),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "categoryTooLong" to Messages.errorCategoryTooLong(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "hostRequired" to Messages.errorHostRequired(),
            "portInvalid" to Messages.errorPortInvalid(),
            "timeoutMsInvalid" to Messages.errorTimeoutMsInvalid(),
            "latencyThresholdInvalid" to Messages.errorLatencyThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
            "proxyNotConfigured" to Messages.errorProxyNotConfigured(),
        ),
        extraFormArgs = listOf(
            "'$proxySelectId'",
            globals.configuredProxies.asJsonString(),
            Messages.proxyNotConfiguredSuffix().asJsonString(),
        ),
        extraSettings = { isReadOnlyMode, settingsAccordionId ->
            accordionItem(
                id = "tcp-monitor-evaluation-settings",
                parentId = settingsAccordionId,
                title = Messages.evaluationSettingsLabel(),
                titleIcon = Icon.LIST_CHECK,
            ) {
                formRow {
                    formCol(COL_LG_4) {
                        validatedInput(
                            propName = "timeoutMs",
                            label = Messages.timeoutMsLabel(),
                            placeholder = null,
                            description = Messages.timeoutMsDescription(),
                            required = true,
                            onInput = "validateTimeoutMs()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(
                                Validation.MIN_TIMEOUT_MILLIS,
                                Validation.MAX_TIMEOUT_MILLIS,
                                step = 100,
                            ),
                        )
                    }
                    formCol(COL_LG_4) {
                        validatedInput(
                            propName = "latencyThresholdMs",
                            label = Messages.latencyThresholdLabel(),
                            placeholder = null,
                            description = Messages.latencyThresholdDescription(),
                            required = false,
                            onInput = "validateLatencyThreshold()",
                            disabledIf = "$isReadOnlyMode",
                            numberRange = NumberRange(Validation.MIN_LATENCY_THRESHOLD_MILLIS, step = 100),
                        )
                    }
                    formCol(COL_LG_4) {
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
        formRow {
            formCol(COL_LG_9) {
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
            formCol(COL_LG_3) {
                validatedInput(
                    propName = "port",
                    label = Messages.portLabel(),
                    placeholder = Messages.portPlaceholder(),
                    description = null,
                    required = true,
                    onInput = "validatePort()",
                    disabledIf = "$isReadOnlyMode",
                    numberRange = NumberRange(Validation.MIN_PORT, Validation.MAX_PORT),
                )
            }
        }
        // Proxy
        div {
            classes(MB_3)
            testId("proxy-select")
            proxySelector(
                selectId = proxySelectId,
                description = Messages.tcpProxyDescription(),
                isReadOnlyMode = isReadOnlyMode,
                currentProxy = monitor?.proxy,
                configuredProxies = globals.configuredProxies,
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
