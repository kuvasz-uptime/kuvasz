package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderSettings(globals: AppGlobals, settings: SettingsDto) =
    withLayout(
        globals,
        title = Messages.settings(),
        pageTitle = { settingsPageHeader() }
    ) {
        div {
            classes(ROW)
            div {
                classes(COL_12)
                p {
                    classes(TEXT_SECONDARY)
                    unsafeText(Messages.settingsDisclaimer)
                }
            }
        }

        div {
            classes(ROW, ROW_CARDS)
            enableMasonry()
            // App settings
            settingsCard(
                title = Messages.applicationSettings(),
                icon = Icon.SETTINGS,
            ) {
                div {
                    classes(DIVIDE_Y)
                    settingsLabel(label = Messages.appVersion(), value = settings.app.version) {
                        // Showing the update icon if a new version is available
                        inlineVersionUpdateBadge(globals.versionInfo())
                    }
                    settingsToggle(label = Messages.updateChecks(), checked = settings.app.updateChecksEnabled)
                    settingsLabel(label = Messages.language(), value = settings.app.language)
                    settingsLabel(
                        label = Messages.eventDataRetention(),
                        value = Messages.xDays(settings.app.eventDataRetentionDays.toString())
                    )
                    settingsLabel(
                        label = Messages.latencyDataRetention(),
                        value = Messages.xDays(settings.app.latencyDataRetentionDays.toString())
                    )
                    settingsLabel(
                        label = Messages.httpCheckTimeout(),
                        value = Messages.xSeconds(settings.app.httpCheckTimeoutSeconds.toString())
                    )
                    settingsToggle(label = Messages.eventLogging(), checked = settings.app.eventLoggingEnabled)
                    settingsToggle(label = Messages.authentication(), checked = settings.authentication.enabled)
                    settingsLabel(
                        label = Messages.authenticationMaxAge(),
                        value = Messages.xSeconds(settings.authentication.accessTokenMaxAge.toString())
                    )
                    settingsToggle(
                        label = Messages.httpMonitorsReadOnlyMode(),
                        checked = settings.app.editabilityState.areHttpMonitorsReadOnly
                    )
                    settingsToggle(
                        label = Messages.pushMonitorsReadOnlyMode(),
                        checked = settings.app.editabilityState.arePushMonitorsReadOnly
                    )
                    settingsToggle(
                        label = Messages.icmpMonitorsReadOnlyMode(),
                        checked = settings.app.editabilityState.areIcmpMonitorsReadOnly
                    )
                    settingsToggle(
                        label = Messages.statusPagesReadOnlyMode(),
                        checked = settings.app.editabilityState.areStatusPagesReadOnly
                    )
                }
            }
            // Integration settings
            settingsCard(
                title = Messages.integrationSettings(),
                icon = Icon.PLUG,
            ) {
                div {
                    classes(DIVIDE_Y)
                    // SMTP settings
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.SMTP)
                            span {
                                classes(MS_2)
                                +"SMTP"
                            }
                        }
                        div {
                            classes(MT_3)
                            settings.smtp?.let { smtpConfig ->
                                multiSettingsLabel(
                                    label = Messages.smtpHost(),
                                    value = smtpConfig.host
                                )
                                multiSettingsLabel(
                                    label = Messages.smtpPort(),
                                    value = smtpConfig.port.toString()
                                )
                                multiSettingsLabel(
                                    label = Messages.smtpTransportStrategy(),
                                    value = smtpConfig.transportStrategy
                                )
                            } ?: span {
                                classes(TEXT_SECONDARY)
                                +Messages.notConfigured()
                            }
                        }
                    }
                }
            }
            // Exporter settings
            settingsCard(
                title = Messages.exporterSettings(),
                icon = Icon.PACKAGE_EXPORT,
            ) {
                div {
                    classes(DIVIDE_Y)
                    // Global
                    settingsToggle(label = Messages.enabled(), checked = settings.metricsExport.exportEnabled)
                    // Separate meters export settings
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.CLIPBOARD_DATA)
                            span {
                                classes(MS_2)
                                +Messages.meters()
                            }
                        }
                        div {
                            classes(MT_3)
                            settings.metricsExport.meters.let { metersConfig ->
                                div {
                                    classes(MT_3)
                                    multiSettingsToggle(
                                        label = Messages.httpUptimeStatus(),
                                        checked = metersConfig.httpUptimeStatus,
                                    )
                                    multiSettingsToggle(
                                        label = Messages.httpLatestLatency(),
                                        checked = metersConfig.httpLatestLatency,
                                    )
                                    multiSettingsToggle(label = Messages.sslStatus(), checked = metersConfig.sslStatus)
                                    multiSettingsToggle(label = Messages.sslExpiry(), checked = metersConfig.sslExpiry)
                                    multiSettingsToggle(
                                        label = Messages.pushUptimeStatus(),
                                        checked = metersConfig.pushUptimeStatus
                                    )
                                    multiSettingsToggle(
                                        label = Messages.icmpUptimeStatus(),
                                        checked = metersConfig.icmpUptimeStatus
                                    )
                                    multiSettingsToggle(
                                        label = Messages.icmpLatestLatency(),
                                        checked = metersConfig.icmpLatestLatency
                                    )
                                    multiSettingsToggle(
                                        label = Messages.icmpLatestPacketLoss(),
                                        checked = metersConfig.icmpLatestPacketLoss
                                    )
                                }
                            }
                        }
                    }
                    // Prometheus
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.PACKAGE_EXPORT)
                            span {
                                classes(MS_2)
                                +"Prometheus"
                            }
                        }
                        div {
                            classes(MT_3)
                            settings.metricsExport.exporters.prometheus.let { prometheusConfig ->
                                div {
                                    classes(MT_3)
                                    multiSettingsToggle(
                                        label = Messages.enabled(),
                                        checked = prometheusConfig.enabled,
                                    )
                                    multiSettingsToggle(
                                        label = Messages.prometheusDescriptions(),
                                        checked = prometheusConfig.descriptions,
                                    )
                                }
                            }
                        }
                    }
                    // OpenTelemetry
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.PACKAGE_EXPORT)
                            span {
                                classes(MS_2)
                                +"OpenTelemetry"
                            }
                        }
                        div {
                            classes(MT_3)
                            settings.metricsExport.exporters.openTelemetry.let { otlpConfig ->
                                div {
                                    classes(MT_3)
                                    multiSettingsToggle(
                                        label = Messages.enabled(),
                                        checked = otlpConfig.enabled,
                                    )
                                    multiSettingsLabel(
                                        label = Messages.otlpUrl(),
                                        value = otlpConfig.url,
                                    )
                                    multiSettingsLabel(
                                        label = Messages.otlpStep(),
                                        value = otlpConfig.step,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private fun FlowContent.settingsCard(
    title: String,
    icon: Icon,
    content: FlowContent.() -> Unit,
) {
    div {
        classes(COL_12, COL_MD_6)
        div {
            classes(CARD)
            div {
                classes(CARD_STAMP)
                div {
                    classes(CARD_STAMP_ICON)
                    icon(icon)
                }
            }
            div {
                classes(CARD_BODY)
                div {
                    classes(CARD_TITLE)
                    +title
                }
                content()
            }
        }
    }
}

private fun FlowContent.settingsToggle(
    label: String,
    checked: Boolean,
    multi: Boolean = false,
) {
    div {
        label {
            val effectiveClasses = mutableSetOf(ROW).addIf(multi, MB_2)
            classes(effectiveClasses)
            span {
                classes(CSSClass.COL)
                +label
            }
            span {
                classes(COL_AUTO)
                label {
                    classes(FORM_CHECK, FORM_CHECK_SINGLE, FORM_SWITCH)
                    input {
                        classes(FORM_CHECK_INPUT)
                        type = InputType.checkBox
                        this.checked = checked
                        disabled = true
                    }
                }
            }
        }
    }
}

private fun FlowContent.settingsLabel(
    label: String,
    value: String,
    multi: Boolean = false,
    extraContent: (SPAN.() -> Unit)? = null,
) {
    div {
        label {
            val effectiveClasses = mutableSetOf(ROW).addIf(multi, MB_2)
            classes(effectiveClasses)
            span {
                classes(CSSClass.COL)
                +label
            }
            span {
                classes(COL_AUTO)
                +value
                extraContent?.let { it() }
            }
        }
    }
}

private fun FlowContent.multiSettingsToggle(label: String, checked: Boolean) =
    settingsToggle(label, checked, multi = true)

private fun FlowContent.multiSettingsLabel(label: String, value: String) =
    settingsLabel(label, value, multi = true)

private fun HtmlBlockTag.settingsPageHeader() {
    div {
        classes(CONTAINER_XL)
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER)
            div {
                classes(CSSClass.COL)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER)
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(PAGE_PRETITLE)
                            +Messages.overview()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +Messages.settings()
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            div {
                                classes(DROPDOWN)
                                a(href = "#") {
                                    classes(BTN, DROPDOWN_TOGGLE)
                                    dropdownToggler()
                                    icon(Icon.FLOPPY)
                                    +Messages.backupAndRestore()
                                }
                                div {
                                    classes(DROPDOWN_MENU)
                                    a(href = "/api/v2/monitors/export/yaml") {
                                        classes(DROPDOWN_ITEM)
                                        attributes["download"] = "true"
                                        icon(Icon.DOWNLOAD)
                                        +Messages.downloadMonitorBackup()
                                    }
                                    a(href = "/api/v2/status-pages/export/yaml") {
                                        classes(DROPDOWN_ITEM)
                                        attributes["download"] = "true"
                                        icon(Icon.DOWNLOAD)
                                        +Messages.downloadStatusPageBackup()
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
