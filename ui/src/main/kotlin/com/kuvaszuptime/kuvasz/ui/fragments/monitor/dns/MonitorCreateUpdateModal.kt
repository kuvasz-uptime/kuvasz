package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.dnsMonitorCreateUpdateModal(
    modalId: String,
    monitor: DnsMonitorDetailsDto?,
    globals: AppGlobals,
) {
    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.DNS,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewDnsMonitor(),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "categoryTooLong" to Messages.errorCategoryTooLong(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "hostRequired" to Messages.errorHostRequired(),
            "resolverPortInvalid" to Messages.errorDnsResolverPortInvalid(),
            "timeoutMsInvalid" to Messages.errorTimeoutMsInvalid(),
            "latencyThresholdInvalid" to Messages.errorLatencyThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
            "recordMatcherInvalid" to Messages.errorDnsRecordMatcherInvalid(),
            "responseCodeMatchersConflict" to Messages.errorDnsResponseCodeMatchersConflict(),
        ),
        extraSettings = { isReadOnlyMode, settingsAccordionId ->
            // DNS assertion settings
            accordionItem(
                id = "dns-monitor-assertion-settings",
                parentId = settingsAccordionId,
                title = Messages.evaluationSettingsLabel(),
                titleIcon = Icon.LIST_CHECK,
            ) {
                // Record matchers
                div {
                    classes(MB_3)
                    recordMatchersTable(isReadOnly = isReadOnlyMode)
                }
                // Expected response code
                div {
                    classes(MB_3)
                    formLabel(
                        label = Messages.dnsExpectedResponseCodeLabel(),
                        description = Messages.dnsExpectedResponseCodeDescription(),
                        required = true,
                    )
                    selectGroup(
                        xModelName = "expectedResponseCode",
                        readOnly = isReadOnlyMode,
                        values = DnsResponseCode.entries.map { ValueAndLabel(it.literal, it.literal) },
                        // Switching the code can conflict with the already-added matchers, just like
                        // adding a matcher can conflict with the already-selected code
                        onChange = "validateResponseCodeMatchers()",
                    )
                    templateTag {
                        xIf("errors.recordMatchers")
                        div {
                            classes(INVALID_FEEDBACK, D_BLOCK)
                            xText("errors.recordMatchers")
                        }
                    }
                }
                // Drift detection
                div {
                    classes(MB_3)
                    toggleSwitch(
                        propName = "driftDetectionEnabled",
                        label = Messages.dnsDriftDetectionLabel(),
                        description = Messages.dnsDriftDetectionDescription(),
                        isDisabled = isReadOnlyMode,
                    )
                }
                // The record types drift detection watches
                templateTag {
                    xIf("driftDetectionEnabled")
                div {
                    classes(MB_2)
                    formLabel(
                        label = Messages.dnsDriftRecordTypesLabel(),
                        description = Messages.dnsDriftRecordTypesDescription(),
                        required = false,
                    )
                    div {
                        DnsRecordType.entries.forEach { recordType ->
                            label {
                                classes(FORM_CHECK, FORM_CHECK_INLINE)
                                input(type = InputType.checkBox) {
                                    value = recordType.name
                                    classes(FORM_CHECK_INPUT)
                                    xModel("driftRecordTypes")
                                    if (isReadOnlyMode) disabled = true
                                }
                                span {
                                    classes(FORM_CHECK_LABEL)
                                    +recordType.name
                                }
                            }
                        }
                    }
                }
                }
            }
        },
    ) { isReadOnlyMode ->
// Host (domain name)
        div {
            classes(MB_3)
            validatedInput(
                propName = "host",
                label = Messages.dnsHostLabel(),
                placeholder = Messages.dnsHostPlaceholder(),
                description = null,
                required = true,
                onInput = "validateHost()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Custom resolver host (optional)
        div {
            classes(MB_3)
            validatedInput(
                propName = "resolverHost",
                label = Messages.dnsResolverHostLabel(),
                placeholder = Messages.dnsResolverHostPlaceholder(),
                description = Messages.dnsResolverHostDescription(),
                required = false,
                onInput = null,
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Resolver port
        div {
            classes(MB_3)
            validatedInput(
                propName = "resolverPort",
                label = Messages.dnsResolverPortLabel(),
                placeholder = null,
                description = null,
                required = true,
                onInput = "validateResolverPort()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Transport
        div {
            classes(MB_3)
            formLabel(
                label = Messages.dnsTransportLabel(),
                description = Messages.dnsTransportDescription(),
                required = true,
            )
            selectGroup(
                xModelName = "transport",
                readOnly = isReadOnlyMode,
                values = DnsTransport.entries.map { ValueAndLabel(it.literal, it.literal) },
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
                label = Messages.dnsTimeoutMsLabel(),
                placeholder = null,
                description = Messages.dnsTimeoutMsDescription(),
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
                description = Messages.dnsLatencyThresholdDescription(),
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

private fun FlowContent.recordMatchersTable(isReadOnly: Boolean) {
    formLabel(
        label = Messages.dnsRecordMatchersLabel(),
        description = Messages.dnsRecordMatchersDescription(),
        required = false,
    )
    table {
        classes(TABLE, TABLE_SM, TABLE_VCENTER)
        // Bound to the add-row error so the `.invalid-feedback` below the table becomes visible (Bootstrap only
        // shows it next to an `.is-invalid` sibling). The response-code↔matchers conflict has its own d-block error.
        xBindErrorClass("newMatcher")
        thead {
            tr {
                th { +Messages.dnsRecordTypeLabel() }
                th { +Messages.dnsMatchTypeLabel() }
                th { +Messages.dnsMatchValueLabel() }
                th {}
            }
        }
        tbody {
            templateTag {
                xFor("(matcher, index) in recordMatchers")
                tr {
                    testId("matcher-row")
                    td {
                        classes(TEXT_WRAP)
                        xText("matcher.recordType")
                    }
                    td {
                        classes(TEXT_WRAP)
                        xText("matcher.matchType")
                    }
                    td {
                        classes(TEXT_WRAP, TEXT_BREAK)
                        xText("matcher.value")
                    }
                    td {
                        classes(TEXT_CENTER, PX_3)
                        div {
                            classes(FLEX_NOWRAP)
                            compactIconButton(
                                Icon.TRASH,
                                classes = setOf(TEXT_RED, BTN_SM),
                            ) {
                                testId("remove-matcher-button")
                                xBindDisabled("isRequestLoading || $isReadOnly")
                                xOnClick("removeMatcher(index)")
                            }
                        }
                    }
                }
            }
            tr {
                td {
                    matcherEnumSelect(
                        xModelName = "newMatcherRecordType",
                        testId = "new-matcher-record-type",
                        isReadOnly = isReadOnly,
                        values = DnsRecordType.entries.map { it.name },
                    )
                }
                td {
                    // Re-validate on change: switching to REGEX can turn an already-typed value invalid (and back).
                    matcherEnumSelect(
                        xModelName = "newMatcherMatchType",
                        testId = "new-matcher-match-type",
                        isReadOnly = isReadOnly,
                        values = DnsMatchType.entries.map { it.name },
                        onChange = "validateNewMatcher()",
                    )
                }
                td {
                    validatedInput(
                        propName = "newMatcherValue",
                        label = null,
                        placeholder = Messages.dnsMatchValuePlaceholder(),
                        required = false,
                        disabledIf = "isRequestLoading || $isReadOnly",
                        onInput = "validateNewMatcher()",
                        smallControl = true,
                    )
                }
                td {
                    classes(PX_3, TEXT_CENTER)
                    compactIconButton(
                        classes = setOf(BTN, BTN_SM, TEXT_GREEN),
                        icon = Icon.PLUS,
                    ) {
                        testId("add-matcher-button")
                        xOnClick("addMatcher()")
                        xBindDisabled("!isMatcherAddable || isRequestLoading || $isReadOnly")
                    }
                }
            }
        }
    }
    templateTag {
        xIf("errors.newMatcher")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.newMatcher")
        }
    }
}

private fun FlowContent.matcherEnumSelect(
    xModelName: String,
    testId: String,
    isReadOnly: Boolean,
    values: List<String>,
    onChange: String? = null,
) {
    select {
        classes(FORM_SELECT, FORM_SELECT_SM)
        testId(testId)
        xModel(xModelName)
        onChange?.let { xOnChange(it) }
        if (isReadOnly) disabled = true
        values.forEach { value ->
            option {
                this.value = value
                +value
            }
        }
    }
}
