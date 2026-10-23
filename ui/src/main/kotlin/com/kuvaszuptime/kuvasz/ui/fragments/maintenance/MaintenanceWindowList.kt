package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

internal const val CREATE_MAINTENANCE_WINDOW_MODAL_ID = "create-maintenance-window-modal"
internal const val MAINTENANCE_WINDOW_LIST_ID = "maintenance-window-list"
private const val MAINTENANCE_WINDOW_LIST_PATH = "/maintenance-windows"

/** The filters of the maintenance window list, named after the query parameters of the v3 API. */
class MaintenanceWindowListFilters internal constructor(params: QueryParams) : ListFilters {

    val name: TextFilter = textFilter(params, "name", Messages.name(), placeholder = Messages.searchByName())

    val enabled: ChoiceFilter<Boolean> = yesNoFilter(params, "enabled", Messages.enabled())

    override val all: List<ListFilter> = listOf(name, enabled)
}

typealias MaintenanceWindowListQuery = ListQuery<MaintenanceWindowSortField, MaintenanceWindowListFilters>

/** The state of the maintenance window list, parsed from the query parameters of its page or fragment. */
fun maintenanceWindowListQuery(params: QueryParams): MaintenanceWindowListQuery = ListQuery.parse(
    params = params,
    path = MAINTENANCE_WINDOW_LIST_PATH,
    filters = MaintenanceWindowListFilters(params),
    sortFields = MaintenanceWindowSortField.entries,
    defaultSorting = ListDefaults.MAINTENANCE_WINDOW_SORTING,
)

fun renderMaintenanceWindowList(
    maintenanceWindows: PagedResult<MaintenanceWindowDetailsDto>,
    query: MaintenanceWindowListQuery,
    appGlobals: AppGlobals,
): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = appGlobals.editabilityState.areMaintenanceWindowsReadOnly()
        div {
            // The page shown, which is the last one if the requested page is past the end, see listQuery in kuvasz.js
            attributes["data-list-page"] = "${maintenanceWindows.page}"
            activeFilterCount(MAINTENANCE_WINDOW_LIST_ID, query.activeFilterCount, outOfBand = true)
            when {
                maintenanceWindows.result.isNotEmpty() -> {
                    maintenanceWindowTable(maintenanceWindows.result, query, isReadOnlyMode)
                    listPagination(maintenanceWindows, query)
                }

                query.activeFilterCount > 0 -> emptyState(
                    icon = Icon.FILTER,
                    title = Messages.noMatchingMaintenanceWindows(),
                    subtitle = Messages.noMatchingMaintenanceWindowsDescription(),
                )

                else -> emptyState(
                    icon = Icon.TOOL,
                    title = Messages.noMaintenanceWindowsYet(),
                    subtitle = if (isReadOnlyMode) {
                        Messages.noMaintenanceWindowsReadOnlyDescription()
                    } else {
                        Messages.noMaintenanceWindowsDescription()
                    },
                )
            }
        }
    }

private fun FlowContent.maintenanceWindowTable(
    maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    query: MaintenanceWindowListQuery,
    isReadOnlyMode: Boolean,
) {
    div {
        classes(TABLE_RESPONSIVE)
        table {
            classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
            thead {
                tr {
                    sortableHeader(query, MaintenanceWindowSortField.ID, "ID", setOf(TEXT_CENTER))
                    sortableHeader(query, MaintenanceWindowSortField.NAME, Messages.name())
                    th {
                        classes(D_NONE, D_MD_TABLE_CELL)
                        +Messages.maintenanceWindowTypeLabel()
                    }
                    // Only the windows with a single run have a start, the others come last when sorted by it
                    sortableHeader(
                        query,
                        MaintenanceWindowSortField.START,
                        Messages.schedule(),
                        setOf(D_NONE, D_MD_TABLE_CELL),
                    )
                    sortableHeader(query, MaintenanceWindowSortField.ENABLED, Messages.status(), setOf(TEXT_CENTER))
                    th {
                        classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                        +Messages.monitors()
                    }
                    th {
                        classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                        +Messages.categories()
                    }
                    // Actions
                    th {}
                }
            }
            tbody {
                maintenanceWindows.forEach { window -> maintenanceWindowListItem(isReadOnlyMode, window) }
            }
        }
    }
}

private fun TBODY.maintenanceWindowListItem(isReadOnlyMode: Boolean, window: MaintenanceWindowDetailsDto) {
    val type = window.resolveType()
    tr {
        testId("maintenance-window-row")
        val editTitle = if (isReadOnlyMode) {
            Messages.configurationOf(window.name)
        } else {
            Messages.updateMaintenanceWindow(window.name)
        }
        // A copy starts disabled, so it doesn't put any monitor into maintenance before it's adjusted
        val clonedFields = mapOf(
            "name" to Messages.clonedMaintenanceWindowName(window.name),
            "enabled" to false,
        ).asJsonString()
        xData(
            "maintenanceWindowListItem(${window.id}, ${window.enabled}, $clonedFields, ${editTitle.asJsonString()})"
        )
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
        // Categories
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
            testId("maintenance-window-categories")
            // A global window covers everything, so its selectors are irrelevant, see the cell above
            if (window.global) +"-" else +window.categories.size.toString()
        }
        // Actions
        td {
            classes(TEXT_END)
            val deleteModalId = "delete-maintenance-window-modal-${window.id}"
            div {
                classes(FLEX_NOWRAP, BTN_GROUP, BTN_GROUP_SM)
                compactIconButton(Icon.SETTINGS) {
                    testId(
                        if (isReadOnlyMode) {
                            "maintenance-window-configuration-button"
                        } else {
                            "maintenance-window-configure-button"
                        }
                    )
                    modalOpener(CREATE_MAINTENANCE_WINDOW_MODAL_ID)
                    xOnClick("editMaintenanceWindow()")
                }
                if (!isReadOnlyMode) {
                    compactIconButton(Icon.COPY) {
                        testId("maintenance-window-clone-button")
                        modalOpener(CREATE_MAINTENANCE_WINDOW_MODAL_ID)
                        xOnClick("cloneMaintenanceWindow()")
                    }
                    val toggleIcon = if (window.enabled) Icon.PAUSE else Icon.PLAY
                    compactIconButton(toggleIcon) {
                        testId("maintenance-window-toggle-button")
                        xBindDisabled("isRequestLoading")
                        xOnClick("toggleMaintenanceWindow()")
                    }
                    compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                        testId("maintenance-window-delete-button")
                        xBindDisabled("isRequestLoading")
                        modalOpener(deleteModalId)
                    }
                }
            }
            if (!isReadOnlyMode) {
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
    val icon = if (window.enabled) {
        Icon.CIRCLE_CHECK_FILLED
    } else {
        Icon.CIRCLE_X_FILLED
    }
    val colorClass: CSSClass = when {
        window.active -> TEXT_GREEN
        window.enabled -> TEXT_YELLOW
        else -> TEXT_SECONDARY
    }

    span {
        classes(colorClass)
        testId("maintenance-window-status")
        tooltip(window.statusText())
        icon(icon)
    }
}
