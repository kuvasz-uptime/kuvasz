package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.serde.*
import com.kuvaszuptime.kuvasz.ui.utils.*
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
    label {
        val labelClasses = mutableSetOf(FORM_LABEL).addIf(required, REQUIRED)
        classes(labelClasses)
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
        name = "monitor-$propName-input"
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
) {
    label {
        classes(FORM_CHECK, FORM_SWITCH)
        input(type = InputType.checkBox) {
            classes(FORM_CHECK_INPUT)
            xModel(propName)
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

internal fun FlowContent.monitorCreateUpdateModal(modalId: String, monitor: MonitorDetailsDto?) {
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
                        description = Messages.sslCheckSwitchDescription()
                    )
                    validatedInput(
                        propName = "sslExpiryThreshold",
                        label = Messages.sslExpiryThresholdLabel(),
                        description = Messages.sslExpiryThresholdDescription(),
                        placeholder = null,
                        required = true,
                        onInput = "validateSslExpiryThreshold()",
                        disabledIf = "!sslCheckEnabled",
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
                            description = Messages.latencyHistorySwitchDescription()
                        )
                    }
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "followRedirects",
                            label = Messages.followRedirectsSwitchLabel(),
                            description = Messages.followRedirectsSwitchDescription()
                        )
                    }
                    div {
                        classes(MB_3)
                        toggleSwitch(
                            propName = "forceNoCache",
                            label = Messages.forceNoCacheSwitchLabel(),
                            description = Messages.forceNoCacheSwitchDescription()
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
                    div {
                        classes(MB_3)
                        label {
                            classes(FORM_LABEL)
                            +Messages.pagerdutyIntegrationKeyLabel()
                            span {
                                classes(MS_2)
                                tooltip(
                                    title = Messages.pagerdutyIntegrationKeyDescription(),
                                    location = TooltipLocation.RIGHT
                                )
                                icon(Icon.INFO_CIRCLE)
                            }
                        }
                        div {
                            classes(ROW, G_2)
                            div {
                                classes(CSSClass.COL)
                                input(type = InputType.text) {
                                    classes(FORM_CONTROL)
                                    name = "monitor-pagerdutyIntegrationKey-input"
                                    xBindDisabled("isPDKeyInputDisabled")
                                    xModel("pdIntegrationKey")
                                }
                            }
                            div {
                                classes(COL_AUTO)
                                templateTag {
                                    xIf("wasPDIntegrationKeyPresent && isPDKeyInputDisabled")
                                    compactIconButton(Icon.EDIT) {
                                        xOnClick("enablePDIntegrationKeyInput()")
                                    }
//                                    button {
//                                        classes(BTN, BTN_ICON)
//                                        xOnClick("enablePDIntegrationKeyInput()")
//                                        icon(Icon.EDIT)
//                                    }
                                }
                                templateTag {
                                    xIf("wasPDIntegrationKeyPresent && isPDKeyInputDisabled && pdIntegrationKey")
                                    compactIconButton(Icon.TRASH, classes = setOf(BTN_OUTLINE_DANGER, MS_2)) {
                                        xOnClick("deletePDIntegrationKey()")
                                    }
//                                    button {
//                                        classes(BTN, BTN_ICON, BTN_OUTLINE_DANGER, MS_2)
//                                        xOnClick("deletePDIntegrationKey()")
//                                        icon(Icon.TRASH)
//                                    }
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
                        +Messages.cancel()
                    }
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
