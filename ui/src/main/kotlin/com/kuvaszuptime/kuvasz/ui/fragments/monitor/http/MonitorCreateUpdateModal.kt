package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.checks.KnownHttpHeaders
import com.kuvaszuptime.kuvasz.models.checks.SupportedExpectedHttpStatusCodes
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.httpMonitorCreateUpdateModal(
    modalId: String,
    monitor: HttpMonitorDetailsDto?,
    globals: AppGlobals,
) {
    val serializedMonitor: String? = monitor?.let { objectMapper.writeValueAsString(it) }
    val serializedErrorMessages = objectMapper.writeValueAsString(
        mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "urlRequired" to Messages.errorMissingUrl(),
            "urlInvalid" to Messages.errorInvalidUrl(),
            "nameAlreadyExists" to Messages.errorNameAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "sslExpiryThresholdInvalid" to Messages.errorSSLExpiryThresholdInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "responseTimeThresholdInvalid" to Messages.errorResponseTimeThresholdInvalid(),
            "requestHeaderInvalid" to Messages.errorNewHeaderInvalid(),
            "expectedHeaderInvalid" to Messages.errorNewHeaderInvalid(),
            "requestBodyInvalid" to Messages.errorRequestBodyInvalid(),
        )
    )
    val serializedStatusCodes = objectMapper.writeValueAsString(SupportedExpectedHttpStatusCodes.allCodes)
    val modalClosedEvent = "http-monitor-upsert-modal-closed"
    val acceptedStatusCodeSelectId = "accepted-status-codes-select"
    val isReadOnlyMode = globals.editabilityState.areHttpMonitorsReadOnly()
    val isMonitorNameReadOnly = monitor?.statusPages?.isNotEmpty() == true &&
        globals.editabilityState.areStatusPagesReadOnly()

    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertHttpMonitorForm(
                |$serializedMonitor, 
                |$serializedErrorMessages, 
                |'$acceptedStatusCodeSelectId', 
                |$serializedStatusCodes,
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
                            +Messages.createNewHttpMonitor()
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
                    // URL
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "url",
                            label = Messages.monitorUrlLabel(),
                            placeholder = Messages.monitorUrlPlaceholder(),
                            required = true,
                            onInput = "validateUrl()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Uptime Check Interval
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "uptimeCheckInterval",
                            label = Messages.uptimeCheckIntervalLabel(),
                            placeholder = null,
                            required = true,
                            onInput = "validateUptimeCheckInterval()",
                            disabledIf = "$isReadOnlyMode",
                        )
                    }
                    // Latency History
                    div {
                        classes(MB_4)
                        toggleSwitch(
                            propName = "latencyHistoryEnabled",
                            label = Messages.latencyHistorySwitchLabel(),
                            description = Messages.latencyHistorySwitchDescription(),
                            isDisabled = isReadOnlyMode,
                        )
                    }

                    // Accordion for all the specific settings
                    val settingsAccordionId = "http-monitor-settings-accordion"
                    accordion(id = settingsAccordionId) {
                        // HTTP Monitor Request Settings
                        accordionItem(
                            id = "http-monitor-request-settings",
                            parentId = settingsAccordionId,
                            title = Messages.requestSettingsLabel(),
                            titleIcon = Icon.ADJUSTMENTS_SHARE,
                        ) {
                            // HTTP Method (GET, HEAD, etc.)
                            div {
                                formLabel(
                                    label = Messages.httpMethodLabel(),
                                    description = Messages.httpMethodDescription(),
                                    inputName = "requestMethod",
                                    required = true,
                                )
                                httpMethodSelector(xModelName = "requestMethod", isReadOnly = isReadOnlyMode)
                            }
                            // Follow Redirects
                            div {
                                classes(MB_3)
                                toggleSwitch(
                                    propName = "followRedirects",
                                    label = Messages.followRedirectsSwitchLabel(),
                                    description = Messages.followRedirectsSwitchDescription(),
                                    isDisabled = isReadOnlyMode,
                                )
                            }
                            // Force no-cache header
                            div {
                                classes(MB_3)
                                toggleSwitch(
                                    propName = "forceNoCache",
                                    label = Messages.forceNoCacheSwitchLabel(),
                                    description = Messages.forceNoCacheSwitchDescription(),
                                    isDisabled = isReadOnlyMode,
                                )
                            }
                            // Custom Headers
                            div {
                                classes(MT_4, MB_3)
                                headersTable(
                                    label = Messages.requestHeadersLabel(),
                                    description = Messages.requestHeadersDescription(),
                                    errorProp = "newRequestHeader",
                                    isReadOnly = isReadOnlyMode,
                                    xModelName = "requestHeaders",
                                    xNewKeyModelName = "newRequestHeaderKey",
                                    xNewValueModelName = "newRequestHeaderValue",
                                    onInput = "validateNewRequestHeader()",
                                    onRemove = "removeRequestHeader(key)",
                                    onAdd = "addRequestHeader()",
                                    newHeaderValidator = "isRequestHeaderAddable",
                                )
                            }
                            // Request body
                            div {
                                validatedTextArea(
                                    propName = "requestBody",
                                    label = Messages.requestBodyLabel(),
                                    description = Messages.requestBodyDescription(),
                                    placeholder = Messages.requestBodyPlaceholder(),
                                    required = false,
                                    onInput = "validateRequestBody()",
                                    disabledIf = "isRequestLoading || $isReadOnlyMode",
                                )
                            }
                        }

                        // HTTP Monitor Evaluation Settings
                        accordionItem(
                            id = "http-monitor-evaluation-settings",
                            parentId = settingsAccordionId,
                            title = Messages.evaluationSettingsLabel(),
                            titleIcon = Icon.LIST_CHECK,
                        ) {
                            // Accepted status codes
                            div {
                                classes(MB_3)
                                formLabel(
                                    label = Messages.expectedStatusCodesLabel(),
                                    description = Messages.expectedStatusCodesDescription(),
                                    inputName = acceptedStatusCodeSelectId,
                                    required = false,
                                )
                                acceptedStatusCodeSelector(
                                    xModelName = "selectedHttpStatusCodes",
                                    acceptedStatusCodeSelectId = acceptedStatusCodeSelectId,
                                    isReadOnly = isReadOnlyMode,
                                )
                            }
                            //Failure count threshold
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
                            // Expected Keyword
                            div {
                                classes(MB_3)
                                validatedInput(
                                    propName = "expectedKeyword",
                                    label = Messages.expectedKeywordLabel(),
                                    description = Messages.expectedKeywordDescription(),
                                    placeholder = null,
                                    required = false,
                                    onInput = null,
                                    disabledIf = "$isReadOnlyMode",
                                )
                            }
                            // Expected Keyword Case Sensitivity
                            div {
                                classes(MB_3)
                                toggleSwitch(
                                    propName = "expectedKeywordCaseSensitive",
                                    label = Messages.expectedKeywordCaseSensitiveLabel(),
                                    description = Messages.expectedKeywordCaseSensitiveDescription(),
                                    isDisabled = isReadOnlyMode,
                                )
                            }
                            // Expected Keyword Negation
                            div {
                                classes(MB_3)
                                toggleSwitch(
                                    propName = "expectedKeywordNegated",
                                    label = Messages.negateExpectedKeywordLabel(),
                                    description = Messages.negateExpectedKeywordDescription(),
                                    isDisabled = isReadOnlyMode,
                                )
                            }
                            // Response Time Threshold
                            div {
                                validatedInput(
                                    propName = "responseTimeThresholdMillis",
                                    label = Messages.responseTimeThresholdLabel(),
                                    description = Messages.responseTimeThresholdDescription(),
                                    placeholder = null,
                                    required = false,
                                    onInput = "validateResponseTimeThreshold()",
                                    disabledIf = "$isReadOnlyMode",
                                    isNumber = true,
                                )
                            }
                            // Expected headers
                            // Custom Headers
                            div {
                                classes(MT_4, MB_3)
                                headersTable(
                                    label = Messages.expectedHeadersLabel(),
                                    description = Messages.expectedHeadersDescription(),
                                    errorProp = "newExpectedHeader",
                                    isReadOnly = isReadOnlyMode,
                                    xModelName = "expectedHeaders",
                                    xNewKeyModelName = "newExpectedHeaderKey",
                                    xNewValueModelName = "newExpectedHeaderValue",
                                    onInput = "validateNewExpectedHeader()",
                                    onRemove = "removeExpectedHeader(key)",
                                    onAdd = "addExpectedHeader()",
                                    newHeaderValidator = "isExpectedHeaderAddable",
                                )
                            }
                        }
                        // SSL Check Settings
                        accordionItem(
                            id = "http-monitor-ssl-check-settings",
                            parentId = settingsAccordionId,
                            title = Messages.sslCheckLabel(),
                            titleIcon = Icon.LOCK_QUESTION,
                        ) {
                            toggleSwitch(
                                propName = "sslCheckEnabled",
                                label = Messages.enabled(),
                                description = Messages.sslCheckSwitchDescription(),
                                isDisabled = isReadOnlyMode,
                            )
                            validatedInput(
                                propName = "sslExpiryThreshold",
                                label = Messages.sslExpiryThresholdLabel(),
                                description = Messages.sslExpiryThresholdDescription(),
                                placeholder = null,
                                required = true,
                                onInput = "validateSslExpiryThreshold()",
                                disabledIf = "$isReadOnlyMode || !sslCheckEnabled",
                            )
                        }
                        // Integration Settings
                        integrationsAccordionItem(
                            elementId = "http-monitor-integration-settings",
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

private fun FlowContent.acceptedStatusCodeSelector(
    xModelName: String,
    acceptedStatusCodeSelectId: String,
    isReadOnly: Boolean,
) {
    select {
        classes(FORM_SELECT)
        id = acceptedStatusCodeSelectId
        multiple = true
        xModel(xModelName)
        xInitNextTick(
            """{ new TomSelect(
                    '#$acceptedStatusCodeSelectId', { 
                        maxOptions: null,
                        valueField: 'value',
                        searchField: 'text',
                        plugins: ['clear_button', 'remove_button'],
                        render: {
                            option: renderStatusCodeOption,
                            item: renderStatusCodeItem
                        },
                        onItemAdd: function(data, item) {
                            this.setTextboxValue('');
                        }
                    }
                    )}
            """.trimMargin()
        )
        if (isReadOnly) disabled = true
        templateTag {
            xFor("status in supportedHttpStatusCodes")
            xBindKey("status.code")
            optionTag {
                xBindValue("status.code")
                xText("[status.code, status.reason].join(' - ')")
                xBindSelected("selectedHttpStatusCodes.includes(status.code.toString())")
            }
        }
    }
}

private fun FlowContent.httpMethodSelector(xModelName: String, isReadOnly: Boolean) {
    selectGroup(
        xModelName = xModelName,
        readOnly = isReadOnly,
        values = HttpMethod.entries.map { method -> ValueAndLabel(value = method.literal, label = method.literal) }
    )
}

private fun FlowContent.headersTable(
    label: String,
    description: String,
    errorProp: String,
    isReadOnly: Boolean,
    xModelName: String,
    xNewKeyModelName: String,
    xNewValueModelName: String,
    onInput: String,
    onRemove: String,
    onAdd: String,
    newHeaderValidator: String,
) {
    formLabel(
        label = label,
        description = description,
        required = false,
    )
    table {
        classes(TABLE, TABLE_SM, TABLE_VCENTER)
        xBindErrorClass(errorProp)
        thead {
            tr {
                th { +Messages.headerNameLabel() }
                th { +Messages.headerValueLabel() }
                th {}
            }
        }
        tbody {
            templateTag {
                xFor("[key, value] in Object.entries($xModelName)")
                tr {
                    td {
                        classes(TEXT_WRAP)
                        xText("key")
                    }
                    td {
                        classes(TEXT_WRAP, TEXT_BREAK)
                        xText("value")
                    }
                    td {
                        classes(TEXT_CENTER, PX_3)
                        div {
                            classes(FLEX_NOWRAP)
                            compactIconButton(
                                Icon.TRASH,
                                classes = setOf(TEXT_RED, BTN_SM),
                            ) {
                                xBindDisabled("isRequestLoading || $isReadOnly")
                                xOnClick(onRemove)
                            }
                        }
                    }
                }
            }
            tr {
                td {
                    validatedInput(
                        propName = xNewKeyModelName,
                        label = null,
                        placeholder = Messages.headerNameLabel(),
                        required = false,
                        disabledIf = "isRequestLoading || $isReadOnly",
                        dataListItems = KnownHttpHeaders.headerNames,
                        onInput = onInput,
                        smallControl = true,
                    )
                }
                td {
                    validatedInput(
                        propName = xNewValueModelName,
                        label = null,
                        placeholder = Messages.headerValueLabel(),
                        required = false,
                        disabledIf = "isRequestLoading || $isReadOnly",
                        onInput = onInput,
                        smallControl = true,
                    )
                }
                td {
                    classes(PX_3, TEXT_CENTER)
                    compactIconButton(
                        classes = setOf(BTN, BTN_SM, TEXT_GREEN),
                        icon = Icon.PLUS,
                    ) {
                        xOnClick(onAdd)
                        xBindDisabled("!$newHeaderValidator || isRequestLoading || $isReadOnly")
                    }
                }
            }
        }
    }
    templateTag {
        xIf("errors.$errorProp")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.$errorProp")
        }
    }
}
