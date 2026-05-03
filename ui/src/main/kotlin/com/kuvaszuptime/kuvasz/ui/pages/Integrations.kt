package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.integration.EmailNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.IntegrationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderIntegrations(globals: AppGlobals, integrations: List<IntegrationConfigDto>, settings: SettingsDto) =
    withLayout(
        globals,
        title = Messages.integrationsLabel(),
        pageTitle = { integrationsPageHeader() }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    if (integrations.isNotEmpty()) {
                        div {
                            classes(CARD_TABLE, TABLE_RESPONSIVE)
                            table {
                                classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                                thead {
                                    tr {
                                        // Icon
                                        th { classes(TEXT_CENTER) }
                                        // ID
                                        th { +"ID" }
                                        // Events
                                        th {
                                            classes(TEXT_CENTER)
                                            +Messages.triggers()
                                        }
                                        // Enabled, global
                                        th { classes(TEXT_CENTER) }
                                        // Test
                                        th {
                                            classes(TEXT_CENTER)
                                            +Messages.test()
                                        }
                                    }
                                }
                                tbody {
                                    integrations.forEach { integration ->
                                        val issue: String? =
                                            if (integration is EmailNotificationConfigDto && settings.smtp == null) {
                                                Messages.emailNotEnabledDueToMissingSMTP()
                                            } else {
                                                null
                                            }
                                        tr {
                                            xData("integrationListItem('${integration.id}')")
                                            // Icon
                                            td {
                                                classes(TEXT_CENTER)
                                                span {
                                                    integration.type.toCssColor()?.let { classes(it) }
                                                    icon(integration.type.icon)
                                                }
                                            }
                                            // ID & optional details
                                            td {
                                                classes(TEXT_WRAP, TEXT_BREAK)
                                                code { +integration.id.toString() }
                                                if (integration is EmailNotificationConfigDto) {
                                                    span {
                                                        classes(MS_2)
                                                        tooltip(
                                                            title = Messages.emailFromToDescription(
                                                                integration.fromAddress,
                                                                integration.toAddress,
                                                            ),
                                                            html = true,
                                                        )
                                                        icon(Icon.INFO_CIRCLE)
                                                    }
                                                }
                                            }
                                            td {
                                                classes(TEXT_CENTER)
                                                span {
                                                    val handledEventTypes = IntegrationEventType.entries
                                                        .minus(integration.excludedEvents.toSet())
                                                    val tooltipLabel = handledEventTypes
                                                        .joinToString(separator = ", ")
                                                        .ifEmpty { Messages.noEventsForIntegration() }
                                                    inlineStatusBadge(
                                                        text = handledEventTypes.size.toString(),
                                                        color = if (handledEventTypes.isEmpty()) {
                                                            Color.RED_LT
                                                        } else {
                                                            Color.DEFAULT
                                                        },
                                                        icon = Icon.BOLT,
                                                        tooltip = tooltipLabel
                                                    )
                                                }
                                            }
                                            // Enabled, global
                                            td {
                                                classes(TEXT_CENTER)
                                                val stateIcon = if (issue != null) {
                                                    Icon.CIRCLE_EXCLAMATION_FILLED
                                                } else if (integration.global) {
                                                    Icon.WORLD
                                                } else {
                                                    Icon.CIRCLE_CHECK_FILLED
                                                }
                                                val colorClass: CSSClass? = if (issue != null) {
                                                    TEXT_RED
                                                } else if (integration.enabled) {
                                                    TEXT_GREEN
                                                } else {
                                                    null
                                                }

                                                span {
                                                    colorClass?.let { classes(it) }
                                                    issue?.let { tooltip(it) }
                                                    icon(stateIcon)
                                                }
                                            }
                                            // Actions
                                            td {
                                                classes(TEXT_CENTER)
                                                // Default state, test button can be clicked
                                                templateTag {
                                                    xIf("!isTestRequestLoading && !wasTestRequestExecuted")
                                                    button {
                                                        classes(setOf(BTN, BTN_ICON))
                                                        if (issue != null) disabled = true
                                                        xOnClick("sendTestRequest()")
                                                        icon(Icon.CIRCLE_DASHED_CHECK)
                                                    }
                                                }
                                                // Loading state, test request is in progress
                                                templateTag {
                                                    xIf("isTestRequestLoading")
                                                    button {
                                                        classes(BTN, BTN_ICON)
                                                        disabled = true
                                                        span {
                                                            classes(SPINNER_GROW, SPINNER_GROW_SM)
                                                            role = "status"
                                                        }
                                                    }
                                                }
                                                // Result state, test request was successful
                                                templateTag {
                                                    xIf("wasTestRequestExecuted && !testRequestError")
                                                    button {
                                                        classes(TEXT_GREEN, BTN, BTN_ICON)
                                                        disabled = true
                                                        icon(Icon.CIRCLE_CHECK_FILLED)
                                                    }
                                                }
                                                // Result state, test request failed
                                                templateTag {
                                                    xIf("wasTestRequestExecuted && testRequestError")
                                                    button {
                                                        classes(TEXT_RED, BTN, BTN_ICON)
                                                        disabled = true
                                                        icon(Icon.CIRCLE_EXCLAMATION_FILLED)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        div {
                            classes(CARD_BODY)
                            p {
                                classes(TEXT_SECONDARY, TEXT_CENTER)
                                +Messages.noIntegrations()
                            }
                        }
                    }
                }
            }
        }
    }

private fun HtmlBlockTag.integrationsPageHeader() {
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
                            +Messages.integrationsLabel()
                            readOnlyBadge(Messages.readOnlyIntegrations())
                        }
                    }
                }
            }
        }
    }
}

private fun IntegrationType.toCssColor(): CSSClass? = when (this) {
    IntegrationType.SLACK -> TEXT_PURPLE
    IntegrationType.TELEGRAM -> TEXT_AZURE
    IntegrationType.PAGERDUTY -> TEXT_GREEN
    IntegrationType.EMAIL -> null
    IntegrationType.DISCORD -> TEXT_INDIGO
    IntegrationType.WEBHOOK -> TEXT_YELLOW
}
