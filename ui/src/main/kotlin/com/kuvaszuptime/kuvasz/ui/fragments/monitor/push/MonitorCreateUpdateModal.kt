package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.Validation
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
            "categoryTooLong" to Messages.errorCategoryTooLong(),
            "nameOrClientSecretAlreadyExists" to Messages.errorNameOrClientSecretAlreadyExists(),
            "nameCannotBeChanged" to Messages.errorNameCannotBeChanged(),
            "heartbeatIntervalInvalid" to Messages.errorHeartbeatIntervalInvalid(),
            "gracePeriodInvalid" to Messages.errorGracePeriodInvalid(),
            "clientSecretInvalid" to Messages.errorClientSecretInvalid(),
            "failureCountThresholdInvalid" to Messages.errorFailureCountThresholdInvalid(),
        ),
    ) { isReadOnlyMode ->
        formRow {
            formCol(COL_LG_4) {
                validatedInput(
                    propName = "heartbeatInterval",
                    label = Messages.heartbeatIntervalLabel(),
                    description = Messages.heartbeatIntervalDescription(),
                    placeholder = null,
                    required = true,
                    onInput = "validateHeartbeatInterval()",
                    disabledIf = "$isReadOnlyMode",
                    numberRange = NumberRange(Validation.MIN_HEARTBEAT_INTERVAL, step = 10),
                )
            }
            formCol(COL_LG_4) {
                validatedInput(
                    propName = "gracePeriod",
                    label = Messages.gracePeriodLabel(),
                    description = Messages.gracePeriodDescription(),
                    placeholder = null,
                    required = true,
                    onInput = "validateGracePeriod()",
                    disabledIf = "$isReadOnlyMode",
                    numberRange = NumberRange(0, step = 60),
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
        // Client secret
        div {
            val propName = "clientSecret"
            classes(MB_3)
            formLabel(
                label = Messages.clientSecretLabel(),
                description = Messages.clientSecretDescription(),
                required = true,
                inputName = fieldId(propName, "input"),
            )
            div {
                classes(ROW)
                div {
                    classes(COL)
                    input {
                        type = InputType.text
                        classes(FORM_CONTROL)
                        id = fieldId(propName, "input")
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
