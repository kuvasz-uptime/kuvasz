package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.pushMonitorCreateUpdateModal(
    modalId: String,
    monitor: PushMonitorDetailsDto?,
    globals: AppGlobals,
) {
    monitorUpsertModal(
        modalId = modalId,
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        monitor = monitor,
        globals = globals,
        createTitle = Messages.createNewPushMonitor(),
        errorMessages = mapOf(
            "nameRequired" to Messages.errorNameRequired(),
            "nameOrClientSecretAlreadyExists" to Messages.errorNameOrClientSecretAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "heartbeatIntervalInvalid" to Messages.errorHeartbeatIntervalInvalid(),
            "gracePeriodInvalid" to Messages.errorGracePeriodInvalid(),
            "clientSecretInvalid" to Messages.errorClientSecretInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        ),
    ) { isReadOnlyMode ->
// Heartbeat Interval
        div {
            classes(MB_3)
            validatedInput(
                propName = "heartbeatInterval",
                label = Messages.heartbeatIntervalLabel(),
                description = Messages.heartbeatIntervalDescription(),
                placeholder = null,
                required = true,
                onInput = "validateHeartbeatInterval()",
                disabledIf = "$isReadOnlyMode",
            )
        }
        // Grace period
        div {
            classes(MB_3)
            validatedInput(
                propName = "gracePeriod",
                label = Messages.gracePeriodLabel(),
                description = Messages.gracePeriodDescription(),
                placeholder = null,
                required = true,
                onInput = "validateGracePeriod()",
                disabledIf = "$isReadOnlyMode",
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
        // Client secret
        div {
            val propName = "clientSecret"
            classes(MB_3)
            formLabel(
                label = Messages.clientSecretLabel(),
                description = Messages.clientSecretDescription(),
                required = true,
                inputName = "$propName-input",
            )
            div {
                classes(ROW)
                div {
                    classes(COL)
                    input {
                        type = InputType.text
                        classes(FORM_CONTROL)
                        autoComplete = "off"
                        xModel(propName)
                        xBindErrorClass(propName)
                        xOnInput("validateClientSecret()")
                        xBindDisabled("$isReadOnlyMode")
                    }
                    templateTag {
                        xIf("errors.$propName")
                        div {
                            classes(INVALID_FEEDBACK)
                            xText("errors.$propName")
                        }
                    }
                }
                div {
                    classes(COL_AUTO)
                    compactIconButton(Icon.REFRESH) {
                        xBindDisabled("$isReadOnlyMode")
                        xOnClick("generateNewClientSecret()")
                    }
                }
                div {
                    classes(COL_AUTO)
                    compactIconButton(Icon.CLIPBOARD) {
                        xOnClick("copyClientSecretToClipboard()")
                    }
                }
            }
        }
        div {
            classes(MB_3)
            div {
                classes(ALERT, ALERT_INFO)
                role = "alert"
                div {
                    classes(ALERT_ICON)
                    icon(Icon.CIRCLE_INFO)
                }
                div {
                    h4 {
                        classes(ALERT_HEADING)
                        +Messages.heartbeatSignalInfoTitle()
                    }
                    div {
                        classes(ALERT_DESCRIPTION)
                        unsafeText(Messages.clientSecretUsageDescription)
                    }
                }
            }
        }
    }
}
