package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

/** The cell linking to a monitor's details page, shared by every table that lists monitors. */
internal fun TR.monitorNameCell(
    monitor: MonitorDetailsDto,
    typeUiConfig: MonitorTypeUiConfig,
    tooltipTitle: String? = null,
    nameBadge: FlowContent.() -> Unit = {},
) {
    td {
        a(href = typeUiConfig.detailsPath(monitor.id)) {
            classes(TEXT_RESET)
            span {
                classes(TEXT_WRAP, TEXT_BREAK)
                tooltipTitle?.let { tooltip(title = it, location = TooltipLocation.RIGHT) }
                +monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH)
            }
        }
        nameBadge()
    }
}

/**
 * The table of a monitor list page, a page of the monitors matching [query], refreshed by htmx. Every type has the
 * name and the status columns plus the row actions; [columns] are the ones describing what the type itself tracks,
 * rendered between the two.
 */
internal fun <T : MonitorDetailsDto, F : Enum<F>> renderMonitorList(
    monitors: PagedResult<T>,
    query: ListQuery<F, *>,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
    nameSortField: F,
    statusSortField: F,
    columns: List<MonitorListColumn<T, F>>,
    // The tooltip of the name cell, for the types that have a target worth showing without opening the monitor
    nameTooltip: (T) -> String? = { null },
    // Rendered next to the name, for the types that have something about the monitor to flag
    nameBadge: FlowContent.(T) -> Unit = {},
): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        div {
            // The page shown, which is the last one if the requested page is past the end, see listQuery in kuvasz.js
            attributes["data-list-page"] = "${monitors.page}"
            activeFilterCount(typeUiConfig.listElementId, query.activeFilterCount, outOfBand = true)
            if (monitors.result.isEmpty()) {
                monitorListEmptyState(typeUiConfig, editabilityState, isFiltered = query.activeFilterCount > 0)
            } else {
                div {
                    classes(TABLE_RESPONSIVE)
                    table {
                        classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                        thead {
                            tr {
                                sortableHeader(query, nameSortField, Messages.name())
                                sortableHeader(query, statusSortField, Messages.status(), setOf(TEXT_CENTER))
                                columns.forEach { column ->
                                    if (column.sortField != null) {
                                        sortableHeader(query, column.sortField, column.header, column.headerClasses)
                                    } else {
                                        th {
                                            classes(column.headerClasses)
                                            +column.header
                                        }
                                    }
                                }
                                // Actions
                                th {}
                            }
                        }
                        tbody {
                            monitors.result.forEach { monitor ->
                                monitorListRow(monitor, typeUiConfig, editabilityState, columns, nameTooltip, nameBadge)
                            }
                        }
                    }
                }
                listPagination(monitors, query)
            }
        }
    }

private fun FlowContent.monitorListEmptyState(
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
    isFiltered: Boolean,
) = when {
    isFiltered -> emptyState(
        icon = Icon.FILTER,
        title = Messages.noMatchingMonitors(),
        subtitle = Messages.noMatchingMonitorsDescription(),
    )

    editabilityState.areMonitorsReadOnly(typeUiConfig.type) -> emptyState(
        icon = typeUiConfig.icon,
        title = Messages.noMonitorsYet(),
        subtitle = Messages.noMonitorsReadOnlyDescription(),
    )

    else -> emptyState(
        icon = typeUiConfig.icon,
        title = Messages.noMonitorsYet(),
        subtitle = Messages.noMonitorsDescription(),
    )
}

private fun <T : MonitorDetailsDto, F : Enum<F>> TBODY.monitorListRow(
    monitor: T,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
    columns: List<MonitorListColumn<T, F>>,
    nameTooltip: (T) -> String?,
    nameBadge: FlowContent.(T) -> Unit,
) {
    tr {
        testId(typeUiConfig.testId("row"))
        val editTitle = if (editabilityState.areMonitorsReadOnly(typeUiConfig.type)) {
            Messages.configurationOf(monitor.name)
        } else {
            Messages.updateMonitor(monitor.name)
        }
        val isNameLocked = monitor.statusPages.isNotEmpty() && editabilityState.areStatusPagesReadOnly()
        xData(
            """${typeUiConfig.alpineComponent("MonitorListItem")}(
            |${monitor.id},
            |${monitor.enabled},
            |${monitor.statusPages.isNotEmpty()},
            |${Messages.clonedMonitorName(monitor.name).asJsonString()},
            |${editTitle.asJsonString()},
            |$isNameLocked
            |)
            """.trimMargin()
        )
        monitorNameCell(monitor, typeUiConfig, nameTooltip(monitor)) { nameBadge(monitor) }
        td {
            classes(TEXT_CENTER)
            uptimeBadgeOfMonitor(monitor, withTooltip = true)
        }
        columns.forEach { column ->
            td {
                classes(column.cellClasses)
                column.cell(this, monitor)
            }
        }
        monitorListRowActions(monitor, typeUiConfig, editabilityState)
    }
}

private fun TR.monitorListRowActions(
    monitor: MonitorDetailsDto,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
) {
    val isReadOnlyMode = editabilityState.areMonitorsReadOnly(typeUiConfig.type)
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    td {
        classes(TEXT_END)
        div {
            classes(FLEX_NOWRAP, BTN_GROUP, BTN_GROUP_SM)
            compactIconButton(Icon.SETTINGS) {
                testId(typeUiConfig.testId(if (isReadOnlyMode) "configuration-button" else "configure-button"))
                modalOpener(typeUiConfig.createModalId)
                xOnClick("editMonitor()")
            }
            if (!isReadOnlyMode) {
                compactIconButton(Icon.COPY) {
                    testId(typeUiConfig.testId("clone-button"))
                    modalOpener(typeUiConfig.createModalId)
                    xOnClick("cloneMonitor()")
                }
                val toggleIcon = if (monitor.enabled) Icon.PAUSE else Icon.PLAY
                compactIconButton(toggleIcon) {
                    testId(typeUiConfig.testId("toggle-button"))
                    xBindDisabled("isRequestLoading")
                    xOnClick("toggleMonitor()")
                }
                compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                    testId(typeUiConfig.testId("delete-button"))
                    xBindDisabled("isRequestLoading")
                    modalOpener(deleteModalId)
                }
            }
        }
        if (!isReadOnlyMode) {
            val isDeleteDisabled = monitor.statusPages.isNotEmpty() && editabilityState.areStatusPagesReadOnly()
            deleteMonitorModal(
                modalId = deleteModalId,
                monitorName = monitor.name,
                isDeleteDisabled = isDeleteDisabled,
            )
        }
    }
}
