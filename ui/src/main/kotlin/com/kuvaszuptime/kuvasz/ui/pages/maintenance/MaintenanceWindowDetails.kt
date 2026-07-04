package com.kuvaszuptime.kuvasz.ui.pages.maintenance

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.maintenance.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderMaintenanceWindowDetailsPage(
    globals: AppGlobals,
    maintenanceWindow: MaintenanceWindowDetailsDto,
    monitorIds: Map<MonitorID, Long>,
): String =
    withLayout(
        globals,
        title = maintenanceWindow.name.abbreviate(MAINTENANCE_WINDOW_NAME_MAX_LENGTH),
        pageTitle = { maintenanceWindowDetailsHeader(maintenanceWindow, globals) },
    ) {
        maintenanceWindowDetailsContent(maintenanceWindow, monitorIds)
    }

internal fun HtmlBlockTag.maintenanceWindowDetailsHeader(
    maintenanceWindow: MaintenanceWindowDetailsDto,
    globals: AppGlobals,
) {
    val deleteModalId = "delete-maintenance-window-modal-${maintenanceWindow.id}"
    val updateModalId = "update-maintenance-window-modal-${maintenanceWindow.id}"
    val isReadOnlyMode = globals.editabilityState.areMaintenanceWindowsReadOnly()

    div {
        classes(CONTAINER)
        xData("maintenanceWindowDetails(${maintenanceWindow.id}, ${maintenanceWindow.enabled})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            maintenanceWindowDetailsHeading(maintenanceWindow)
            div {
                classes(COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    if (!isReadOnlyMode) {
                        button {
                            classes(BTN, BTN_ICON)
                            testId("toggle-maintenance-window-button")
                            xBindDisabled("isRequestLoading")
                            xOnClick("toggleMaintenanceWindow()")
                            template {
                                xIf("isMaintenanceWindowEnabled")
                                icon(Icon.PAUSE)
                            }
                            template {
                                xIf("!isMaintenanceWindowEnabled")
                                icon(Icon.PLAY)
                            }
                        }
                        buttonWithIcon(Icon.SETTINGS, Messages.configure()) {
                            modalOpener(updateModalId)
                            testId("configure-button")
                        }
                        compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                            xBindDisabled("isRequestLoading")
                            modalOpener(deleteModalId)
                        }
                        deleteMaintenanceWindowModal(
                            modalId = deleteModalId,
                            maintenanceWindowName = maintenanceWindow.name,
                        )
                    } else {
                        buttonWithIcon(Icon.EYE, Messages.configuration()) {
                            modalOpener(updateModalId)
                            testId("configuration-button")
                        }
                    }
                    maintenanceWindowCreateUpdateModal(updateModalId, maintenanceWindow, globals)
                }
            }
        }
    }
}

private fun FlowContent.maintenanceWindowDetailsContent(
    window: MaintenanceWindowDetailsDto,
    monitorIds: Map<MonitorID, Long>,
) {
    val type = window.resolveType()
    div {
        classes(ROW, ROW_CARDS)
        div {
            classes(COL_12)
            div {
                classes(CARD)
                div {
                    classes(CARD_TABLE, TABLE_RESPONSIVE)
                    table {
                        classes(TABLE, TABLE_VCENTER, CARD_TABLE)
                        tbody {
                            propertyRow(Messages.maintenanceWindowDescriptionLabel()) {
                                window.description?.let { +it } ?: noData()
                            }
                            propertyRow(Messages.schedule()) {
                                maintenanceWindowScheduleSummary(window, type)
                            }
                            window.nextStart?.let { nextStart ->
                                propertyRow(Messages.maintenanceWindowNextStart()) {
                                    +nextStart.toDateTimeStringWithZone()
                                }
                            }
                            window.endsAt?.let { endsAt ->
                                propertyRow(Messages.maintenanceWindowEndsAt()) {
                                    +endsAt.toDateTimeStringWithZone()
                                }
                            }
                            propertyRow(Messages.maintenanceWindowAffectedMonitors()) {
                                if (window.global) {
                                    inlineBadge(text = Messages.maintenanceWindowGlobalScope(), color = Color.GREEN_LT)
                                } else if (window.monitors.isEmpty()) {
                                    noData()
                                } else {
                                    window.monitors.sortedBy { it.toString() }.forEach { monitor ->
                                        span {
                                            classes(ME_2)
                                            affectedMonitorBadge(monitor, monitorIds[monitor])
                                        }
                                    }
                                }
                            }
                            propertyRow(Messages.integrationsLabel()) {
                                if (window.integrations.isEmpty()) {
                                    noData()
                                } else {
                                    window.integrations.sortedBy { it.toString() }.forEach { integration ->
                                        span {
                                            classes(ME_2)
                                            inlineBadge(text = integration.toString())
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

private fun FlowContent.noData() {
    span {
        classes(TEXT_MUTED)
        +"—"
    }
}

private fun TBODY.propertyRow(label: String, valueContent: FlowContent.() -> Unit) {
    tr {
        th {
            classes(W_25)
            +label
        }
        td { valueContent() }
    }
}

private fun FlowContent.affectedMonitorBadge(monitor: MonitorID, numericId: Long?) {
    if (numericId != null) {
        a(href = "/${monitor.type.identifier}-monitors/${numericId}") {
            classes(TEXT_RESET)
            inlineBadge(text = monitor.toString())
        }
    } else {
        inlineBadge(text = monitor.toString())
    }
}
