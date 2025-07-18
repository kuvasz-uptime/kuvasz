package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderSettings(globals: AppGlobals, settings: SettingsDto) =
    withLayout(
        globals,
        title = Messages.settings(),
        pageTitle = { simplePageHeader(preTitle = Messages.overview(), title = Messages.settings()) }
    ) {
        fun FlowContent.integrationBadge(config: SettingsDto.IntegrationConfigDto) {
            val effective = if (config is SettingsDto.EmailNotificationConfigDto) {
                config.enabled && settings.integrations.smtp != null
            } else {
                config.enabled
            }

            span {
                classes(
                    mutableSetOf(BADGE, BADGE_LG).addIf(effective, BG_GREEN_LT)
                )
                if (config is SettingsDto.EmailNotificationConfigDto && effective != config.enabled) {
                    tooltip(Messages.emailNotEnabledDueToMissingSMTP())
                }
                if (config.global) {
                    icon(Icon.WORLD)
                }
                +config.name
                if (config.enabled != effective) {
                    span {
                        classes(TEXT_YELLOW)
                        icon(Icon.ALERT_TRIANGLE)
                    }
                }
            }
        }

        fun FlowContent.integrationsList(integrations: List<SettingsDto.IntegrationConfigDto>) {
            div {
                classes(BADGES_LIST)
                integrations.sortedBy { it.name }.forEach { integrationBadge(it) }
                if (integrations.isEmpty()) {
                    span { +Messages.notConfigured() }
                }
            }
        }

        div {
            classes(ROW, ROW_CARDS)
            p {
                classes(TEXT_SECONDARY)
                unsafeText(Messages.settingsDisclaimer)
            }
            // App settings
            settingsCard(
                title = Messages.applicationSettings(),
                icon = Icon.SETTINGS,
            ) {
                div {
                    classes(DIVIDE_Y)
                    settingsLabel(label = Messages.appVersion(), value = settings.app.version)
                    settingsLabel(label = Messages.language(), value = settings.app.language)
                    settingsLabel(
                        label = Messages.eventDataRetention(),
                        value = Messages.xDays(settings.app.eventDataRetentionDays.toString())
                    )
                    settingsLabel(
                        label = Messages.latencyDataRetention(),
                        value = Messages.xDays(settings.app.latencyDataRetentionDays.toString())
                    )
                    settingsToggle(label = Messages.eventLogging(), checked = settings.app.eventLoggingEnabled)
                    settingsToggle(label = Messages.readOnlyMode(), checked = settings.app.readOnlyMode)
                    settingsToggle(label = Messages.authentication(), checked = settings.authentication.enabled)
                    settingsLabel(
                        label = Messages.authenticationMaxAge(),
                        value = Messages.xSeconds(settings.authentication.accessTokenMaxAge.toString())
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
                            settings.integrations.smtp?.let { smtpConfig ->
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
                    // Email integrations
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.ENVELOPE)
                            span {
                                classes(MS_2)
                                +"E-mail"
                            }
                        }
                        integrationsList(settings.integrations.email)
                    }
                    // PagerDuty integrations
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.BRAND_PAGERDUTY)
                            span {
                                classes(MS_2)
                                +"PagerDuty"
                            }
                        }
                        integrationsList(settings.integrations.pagerduty)
                    }

                    // Slack integrations
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.BRAND_SLACK)
                            span {
                                classes(MS_2)
                                +"Slack"
                            }
                        }
                        integrationsList(settings.integrations.slack)
                    }

                    // Discord integrations
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.BRAND_DISCORD)
                            span {
                                classes(MS_2)
                                +"Discord"
                            }
                        }
                        integrationsList(settings.integrations.discord)
                    }

                    // Telegram integrations
                    div {
                        div {
                            classes(FORM_LABEL)
                            icon(Icon.BRAND_TELEGRAM)
                            span {
                                classes(MS_2)
                                +"Telegram"
                            }
                        }
                        integrationsList(settings.integrations.telegram)
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
                                        label = Messages.uptimeStatus(),
                                        checked = metersConfig.uptimeStatus,
                                    )
                                    multiSettingsToggle(
                                        label = Messages.latestLatency(),
                                        checked = metersConfig.latestLatency,
                                    )
                                    multiSettingsToggle(label = Messages.sslStatus(), checked = metersConfig.sslStatus)
                                    multiSettingsToggle(label = Messages.sslExpiry(), checked = metersConfig.sslExpiry)
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
            }
        }
    }
}

private fun FlowContent.multiSettingsToggle(label: String, checked: Boolean) =
    settingsToggle(label, checked, multi = true)

private fun FlowContent.multiSettingsLabel(label: String, value: String) =
    settingsLabel(label, value, multi = true)
