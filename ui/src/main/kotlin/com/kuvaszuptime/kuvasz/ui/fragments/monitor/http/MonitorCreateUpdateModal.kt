package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.checks.KnownHttpHeaders
import com.kuvaszuptime.kuvasz.models.checks.SupportedExpectedHttpStatusCodes
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.serde.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import de.comahe.i18n4k.strings.capitalize
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
            "sslExpiryThresholdInvalid" to Messages.errorSSLExpiryThresholdInvalid(),
            "uptimeCheckIntervalInvalid" to Messages.errorUptimeCheckIntervalInvalid(),
            "responseTimeThresholdInvalid" to Messages.errorResponseTimeThresholdInvalid(),
            "requestHeaderInvalid" to Messages.errorNewHeaderInvalid(),
            "expectedHeaderInvalid" to Messages.errorNewHeaderInvalid(),
            "requestBodyInvalid" to Messages.errorRequestBodyInvalid(),
        )
    )
    val serializedStatusCodes = objectMapper.writeValueAsString(SupportedExpectedHttpStatusCodes.allCodes)
    val modalClosedEvent = "monitor-upsert-modal-closed"
    val acceptedStatusCodeSelectId = "accepted-status-codes-select"
    val isReadOnlyMode = globals.editabilityState.areHttpMonitorsReadOnly()
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData(
            """upsertMonitorForm(
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
                        validatedInput(
                            propName = "name",
                            label = Messages.monitorNameLabel(),
                            placeholder = Messages.monitorNamePlaceholder(),
                            required = true,
                            onInput = "validateName()",
                            disabledIf = "$isReadOnlyMode",
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
                        accordionItem(
                            id = "http-monitor-integration-settings",
                            parentId = settingsAccordionId,
                            title = Messages.integrationsLabel(),
                            titleIcon = Icon.PLUG,
                            additionalTitleContent = {
                                templateTag {
                                    xIf("integrations.length + globalIntegrationCount === 0")
                                    span {
                                        classes(TEXT_RED)
                                        icon(Icon.CIRCLE_EXCLAMATION_FILLED)
                                    }
                                }
                                templateTag {
                                    xIf("integrations.length + globalIntegrationCount > 0")
                                    span {
                                        classes(TEXT_GREEN)
                                        icon(Icon.CIRCLE_CHECK)
                                    }
                                }
                            },
                        ) {
                            val enabledIntegrationsByType = globals.configuredIntegrationsByType.toSortedMap()
                            div {
                                classes(MB_3)
                                if (enabledIntegrationsByType.isEmpty()) {
                                    p {
                                        classes(TEXT_MUTED)
                                        +Messages.noIntegrationsAvailable()
                                    }
                                }
                                enabledIntegrationsByType.forEach { (type, integrations) ->
                                    // Render each integration type with its integrations
                                    div {
                                        classes(FORM_LABEL, MT_2)
                                        icon(type.icon)
                                        span {
                                            classes(MS_2)
                                            +type.identifier.capitalize()
                                        }
                                    }
                                    div {
                                        // Render each integration as a checkbox
                                        integrations.sortedBy { it.name }.forEach { integration ->
                                            label {
                                                classes(FORM_CHECK, FORM_CHECK_INLINE)
                                                input(type = InputType.checkBox) {
                                                    value = integration.id.toString()
                                                    classes(FORM_CHECK_INPUT)
                                                    xModel("integrations")
                                                    if (isReadOnlyMode) disabled = true
                                                }
                                                span {
                                                    classes(FORM_CHECK_LABEL)
                                                    if (integration.global) {
                                                        span {
                                                            classes(ME_2, TEXT_GREEN)
                                                            tooltip(
                                                                title = Messages.globalIntegrationInfo(),
                                                                location = TooltipLocation.RIGHT
                                                            )
                                                            icon(Icon.WORLD)
                                                        }
                                                    }
                                                    +integration.name
                                                    if (!integration.enabled) {
                                                        span {
                                                            classes(MS_2, TEXT_YELLOW)
                                                            tooltip(
                                                                title = Messages.disabledIntegrationInfo(),
                                                                location = TooltipLocation.RIGHT
                                                            )
                                                            icon(Icon.ALERT_TRIANGLE)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
    // Converting Bootstrap's own modal event to a new one, that is caught by alpine to reset the form's state if it's
    // closed without saving.
    script {
        unsafe {
            +"""
            const modal = document.getElementById('$modalId')
            modal.addEventListener('hide.bs.modal', () => {
                sendWindowEvent('$modalClosedEvent');
            })
            """.trimIndent()
        }
    }
}

internal fun FlowContent.formLabel(
    label: String,
    required: Boolean = false,
    inputName: String? = null,
    description: String? = null
) {
    label {
        val labelClasses = mutableSetOf(FORM_LABEL).addIf(required, REQUIRED)
        classes(labelClasses)
        inputName?.let { htmlFor = it }
        if (required) required()
        +label
        if (!description.isNullOrEmpty()) {
            span {
                classes(MS_2)
                tooltip(
                    title = description,
                    location = TooltipLocation.RIGHT
                )
                icon(Icon.INFO_CIRCLE)
            }
        }
    }
}

internal fun FlowContent.validatedInput(
    propName: String,
    label: String?,
    description: String? = null,
    placeholder: String? = propName,
    required: Boolean = false,
    onInput: String? = null,
    disabledIf: String? = null,
    isNumber: Boolean = false,
    dataListItems: Set<String> = emptySet(),
    smallControl: Boolean = false,
) {
    val inputName = "monitor-$propName-input"
    val dataListId = "monitor-$propName-datalist"
    if (!label.isNullOrEmpty()) {
        formLabel(
            label = label,
            required = required,
            inputName = inputName,
            description = description
        )
    }
    input(type = InputType.text) {
        val classes = mutableSetOf(FORM_CONTROL).addIf(smallControl, FORM_CONTROL_SM)
        classes(classes)
        id = inputName
        name = inputName
        placeholder?.let { this.placeholder = it }
        xBindErrorClass(propName)
        onInput?.let { xOnInput(it) }
        disabledIf?.let { xBindDisabled(it) }
        if (isNumber) xModelNumber(propName) else xModel(propName)
        if (dataListItems.isNotEmpty()) list = dataListId
    }
    if (dataListItems.isNotEmpty()) {
        dataList {
            id = dataListId
            dataListItems.forEach { item ->
                option {
                    value = item
                }
            }
        }
    }
    templateTag {
        xIf("errors.$propName")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.$propName")
        }
    }
}

internal fun FlowContent.validatedTextArea(
    propName: String,
    label: String?,
    description: String? = null,
    placeholder: String? = null,
    required: Boolean = false,
    onInput: String? = null,
    disabledIf: String? = null,
) {
    val inputName = "monitor-$propName-input"
    if (!label.isNullOrEmpty()) {
        formLabel(
            label = label,
            required = required,
            inputName = inputName,
            description = description
        )
    }
    textArea(rows = "4") {
        classes(FORM_CONTROL)
        id = inputName
        name = inputName
        placeholder?.let { this.placeholder = it }
        xModel(propName)
        xBindErrorClass(propName)
        onInput?.let { xOnInput(it) }
        disabledIf?.let { xBindDisabled(it) }
    }
    templateTag {
        xIf("errors.$propName")
        div {
            classes(INVALID_FEEDBACK)
            xText("errors.$propName")
        }
    }
}

internal fun FlowContent.toggleSwitch(
    propName: String,
    label: String,
    description: String? = null,
    isDisabled: Boolean = false,
) {
    label {
        classes(FORM_CHECK, FORM_SWITCH)
        input(type = InputType.checkBox, name = propName) {
            classes(FORM_CHECK_INPUT)
            xModel(propName)
            if (isDisabled) disabled = true
        }
        span {
            classes(FORM_CHECK_LABEL)
            +label
            if (!description.isNullOrEmpty()) {
                span {
                    classes(MS_2)
                    tooltip(
                        title = description,
                        location = TooltipLocation.RIGHT
                    )
                    icon(Icon.INFO_CIRCLE)
                }
            }
        }
    }
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
                            option: function(data, escape) {
                                const statusClass = statusCodeToBadgeClass(data.value);
                                return '<div>' +
                                           '<span class="status-dot ' + statusClass + ' me-2"></span>' +
                                       escape(data.text) +
                                    '</div>';
                            },
                            item: function(data, escape) {
                                const statusClass = statusCodeToBadgeClass(data.value);
                                return '<div>' +
                                 '<span class="status-dot ' + statusClass + ' me-2"></span>' +
                                    escape(data.value) + 
                                '</div>';
                            }
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

private val IntegrationType.icon: Icon
    get() = when (this) {
        IntegrationType.EMAIL -> Icon.ENVELOPE
        IntegrationType.SLACK -> Icon.BRAND_SLACK
        IntegrationType.DISCORD -> Icon.BRAND_DISCORD
        IntegrationType.PAGERDUTY -> Icon.BRAND_PAGERDUTY
        IntegrationType.TELEGRAM -> Icon.BRAND_TELEGRAM
    }
