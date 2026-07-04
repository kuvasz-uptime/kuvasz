package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderMaintenanceWindowList(maintenanceWindows: List<MaintenanceWindowDetailsDto>, appGlobals: AppGlobals): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = appGlobals.editabilityState.areMaintenanceWindowsReadOnly()
        div {
            classes(CARD_TABLE, TABLE_RESPONSIVE)
            table {
                classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                thead {
                    tr {
                        th {
                            classes(TEXT_CENTER)
                            +"ID"
                        }
                        th { +Messages.name() }
                        th {
                            classes(D_NONE, D_MD_TABLE_CELL)
                            +Messages.maintenanceWindowTypeLabel()
                        }
                        th {
                            classes(D_NONE, D_MD_TABLE_CELL)
                            +Messages.schedule()
                        }
                        th {
                            classes(TEXT_CENTER)
                            +Messages.status()
                        }
                        th {
                            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                            +Messages.monitors()
                        }
                        // Actions
                        if (!isReadOnlyMode) {
                            th {}
                        }
                    }
                }
                tbody {
                    if (maintenanceWindows.isEmpty()) {
                        tr {
                            td {
                                attributes["colspan"] = if (isReadOnlyMode) "6" else "7"
                                classes(TEXT_CENTER, TEXT_MUTED, P_3)
                                +Messages.noMaintenanceWindows()
                            }
                        }
                    } else {
                        maintenanceWindows.forEach { window -> maintenanceWindowListItem(isReadOnlyMode, window) }
                    }
                }
            }
        }
    }

private fun TBODY.maintenanceWindowListItem(isReadOnlyMode: Boolean, window: MaintenanceWindowDetailsDto) {
    val type = window.resolveType()
    tr {
        testId("maintenance-window-row")
        xData("maintenanceWindowListItem(${window.id}, ${window.enabled})")
        // ID
        th {
            classes(TEXT_CENTER)
            +window.id.toString()
        }
        // Name
        td {
            a(href = "/maintenance-windows/${window.id}") {
                classes(TEXT_RESET)
                span {
                    classes(TEXT_WRAP, TEXT_BREAK)
                    +window.name.abbreviate(MAINTENANCE_WINDOW_NAME_MAX_LENGTH)
                }
            }
        }
        // Type
        td {
            classes(D_NONE, D_MD_TABLE_CELL)
            inlineBadge(text = type.label(), color = type.badgeColor())
        }
        // Schedule
        td {
            classes(D_NONE, D_MD_TABLE_CELL)
            maintenanceWindowScheduleSummary(window, type)
        }
        // Status
        td {
            classes(TEXT_CENTER)
            maintenanceWindowStatus(window)
        }
        // Monitors
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
            testId("maintenance-window-monitors")
            if (window.global) {
                inlineBadge(text = Messages.maintenanceWindowGlobalScope(), color = Color.GREEN_LT)
            } else {
                +window.monitors.size.toString()
            }
        }
        // Actions
        if (!isReadOnlyMode) {
            td {
                classes(TEXT_CENTER)
                val deleteModalId = "delete-maintenance-window-modal-${window.id}"
                div {
                    classes(FLEX_NOWRAP, BTN_GROUP)
                    val toggleIcon = if (window.enabled) Icon.PAUSE else Icon.PLAY
                    compactIconButton(toggleIcon) {
                        testId("maintenance-window-toggle-button")
                        xBindDisabled("isRequestLoading")
                        xOnClick("toggleMaintenanceWindow()")
                    }
                    compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                        xBindDisabled("isRequestLoading")
                        modalOpener(deleteModalId)
                    }
                }
                deleteMaintenanceWindowModal(
                    modalId = deleteModalId,
                    maintenanceWindowName = window.name,
                )
            }
        }
    }
}

internal fun FlowContent.maintenanceWindowScheduleSummary(
    window: MaintenanceWindowDetailsDto,
    type: MaintenanceWindowType,
) {
    when (type) {
        MaintenanceWindowType.MANUAL -> span {
            classes(TEXT_MUTED)
            +"—"
        }

        MaintenanceWindowType.CRON -> {
            code { +window.cron.orEmpty() }
            window.duration?.let { duration ->
                span {
                    classes(MS_2, TEXT_MUTED)
                    +duration
                }
            }
        }

        MaintenanceWindowType.SINGLE -> {
            window.start?.let { start ->
                +start.toDateTimeStringWithZone()
            }
            window.duration?.let { duration ->
                span {
                    classes(MS_2, TEXT_MUTED)
                    +duration
                }
            }
        }
    }
}

internal fun FlowContent.maintenanceWindowStatus(window: MaintenanceWindowDetailsDto) {
    inlineBadge(
        text = window.statusText(),
        color = window.badgeColor(),
        tooltip = window.endsAt?.let { "${Messages.maintenanceWindowEndsAt()}: ${it.toDateTimeStringWithZone()}" },
    )
}
