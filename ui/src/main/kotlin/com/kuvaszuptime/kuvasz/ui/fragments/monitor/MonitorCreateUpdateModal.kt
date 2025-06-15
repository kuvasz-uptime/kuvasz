package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.serde.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import de.comahe.i18n4k.strings.capitalize
import kotlinx.html.*

internal fun FlowContent.validatedInput(
    propName: String,
    label: String,
    description: String? = null,
    placeholder: String? = propName,
    required: Boolean = false,
    onInput: String? = null,
    disabledIf: String? = null,
    isNumber: Boolean = false,
) {
    val inputName = "monitor-$propName-input"
    label {
        val labelClasses = mutableSetOf(FORM_LABEL).addIf(required, REQUIRED)
        classes(labelClasses)
        htmlFor = inputName
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
    input(type = InputType.text) {
        classes(FORM_CONTROL)
        id = inputName
        name = inputName
        placeholder?.let { this.placeholder = it }
        xBindClass("errors.$propName ? 'is-invalid' : ''")
        onInput?.let { xOnInput(it) }
        disabledIf?.let { xBindDisabled(it) }
        if (isNumber) xModelNumber(propName) else xModel(propName)
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

internal fun FlowContent.monitorCreateUpdateModal(
    modalId: String,
    monitor: MonitorDetailsDto?,
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
        )
    )
    val modalClosedEvent = "monitor-upsert-modal-closed"
    div {
        id = modalId
        classes(MODAL, MODAL_BLUR, ROUNDED, BG_SURFACE_BACKDROP)
        xData("upsertMonitorForm($serializedMonitor, $serializedErrorMessages)")
        attributes["@$modalClosedEvent.window"] = "resetState()"
        attributes["tabindex"] = "-1"
        attributes["role"] = "dialog"

        div {
            classes(MODAL_DIALOG, MODAL_LG, MODAL_DIALOG_CENTERED)
            attributes["role"] = "document"
            role

            div {
                classes(MODAL_CONTENT)
                // Modal header
                div {
                    classes(MODAL_HEADER)
                    h5 {
                        classes(MODAL_TITLE)
                        if (monitor == null) {
                            +Messages.createNewMonitor()
                        } else if (globals.isReadOnlyMode) {
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
                    classes(MODAL_BODY)
                    h3 {
                        classes(MB_3)
                        +Messages.basicSettingsLabel()
                    }
                    // Name
                    div {
                        classes(MB_3)
                        validatedInput(
                            propName = "name",
                            label = Messages.monitorNameLabel(),
                            placeholder = Messages.monitorNamePlaceholder(),
                            required = true,
                            onInput = "validateName()",
                            disabledIf = "${globals.isReadOnlyMode}",
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
                            disabledIf = "${globals.isReadOnlyMode}",
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
                            disabledIf = "${globals.isReadOnlyMode}",
                        )
                    }
                    // HTTP Method (GET, HEAD, etc.)
                    div {
                        label {
                            classes(FORM_LABEL)
                            +Messages.httpMethodLabel()
                        }
                        div {
                            classes(FORM_SELECTGROUP)
                            label {
                                classes(FORM_SELECTGROUP_ITEM)
                                input(type = InputType.radio, name = "http-methods") {
                                    classes(FORM_SELECTGROUP_INPUT)
                                    value = "GET"
                                    xModel("requestMethod")
                                    if (globals.isReadOnlyMode) disabled = true
                                }
                                span {
                                    classes(FORM_SELECTGROUP_LABEL)
                                    +"GET"
                                }
                            }
                            label {
                                classes(FORM_SELECTGROUP_ITEM)
                                input(type = InputType.radio, name = "http-methods") {
                                    classes(FORM_SELECTGROUP_INPUT)
                                    value = "HEAD"
                                    xModel("requestMethod")
                                    if (globals.isReadOnlyMode) disabled = true
                                }
                                span {
                                    classes(FORM_SELECTGROUP_LABEL)
                                    +"HEAD"
                                }
                            }
                        }
                    }
                }
                // SSL Check Settings
                div {
                    classes(MODAL_BODY)
                    h3 {
                        classes(MB_3)
                        +Messages.sslCheckLabel()
                    }
                    toggleSwitch(
                        propName = "sslCheckEnabled",
                        label = Messages.enabled(),
                        description = Messages.sslCheckSwitchDescription(),
                        isDisabled = globals.isReadOnlyMode,
                    )
                    validatedInput(
                        propName = "sslExpiryThreshold",
                        label = Messages.sslExpiryThresholdLabel(),
                        description = Messages.sslExpiryThresholdDescription(),
                        placeholder = null,
                        required = true,
                        onInput = "validateSslExpiryThreshold()",
                        disabledIf = "${globals.isReadOnlyMode} || !sslCheckEnabled",
                    )
                }
                // Advanced Settings
                div {
                    classes(MODAL_BODY)
                    h3 {
                        classes(MB_3)
                        +Messages.advancedSettingsLabel()
                    }
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "latencyHistoryEnabled",
                            label = Messages.latencyHistorySwitchLabel(),
                            description = Messages.latencyHistorySwitchDescription(),
                            isDisabled = globals.isReadOnlyMode,
                        )
                    }
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "followRedirects",
                            label = Messages.followRedirectsSwitchLabel(),
                            description = Messages.followRedirectsSwitchDescription(),
                            isDisabled = globals.isReadOnlyMode,
                        )
                    }
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "forceNoCache",
                            label = Messages.forceNoCacheSwitchLabel(),
                            description = Messages.forceNoCacheSwitchDescription(),
                            isDisabled = globals.isReadOnlyMode,
                        )
                    }
                }
                // Integrations
                div {
                    classes(MODAL_BODY)
                    h3 {
                        classes(MB_3)
                        +Messages.integrationsLabel()
                    }
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
                                            if (globals.isReadOnlyMode) disabled = true
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

                // Modal footer
                div {
                    classes(MODAL_FOOTER)
                    a(href = "#") {
                        classes(BTN, BTN_LINK, LINK_SECONDARY)
                        modalCloser()
                        if (globals.isReadOnlyMode) {
                            +Messages.close()
                        } else {
                            +Messages.cancel()
                        }
                    }
                    if (!globals.isReadOnlyMode) {
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

private val IntegrationType.icon: Icon
    get() = when (this) {
        IntegrationType.EMAIL -> Icon.ENVELOPE
        IntegrationType.SLACK -> Icon.BRAND_SLACK
        IntegrationType.PAGERDUTY -> Icon.BRAND_PAGERDUTY
        IntegrationType.TELEGRAM -> Icon.BRAND_TELEGRAM
    }
