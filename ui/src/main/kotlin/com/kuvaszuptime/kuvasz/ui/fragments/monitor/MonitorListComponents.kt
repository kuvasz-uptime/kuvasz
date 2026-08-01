package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
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
    tooltipTitle: String? = null
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
    }
}

/**
 * The table of a monitor list page, refreshed by htmx. Every type has the name and the status columns plus the row
 * actions; [columns] are the ones describing what the type itself tracks, rendered between the two.
 */
internal fun <T : MonitorDetailsDto> renderMonitorList(
    monitors: List<T>,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
    columns: List<MonitorListColumn<T>>,
    // The tooltip of the name cell, for the types that have a target worth showing without opening the monitor
    nameTooltip: (T) -> String? = { null },
): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = editabilityState.areMonitorsReadOnly(typeUiConfig.type)
        if (monitors.isEmpty()) {
            div {
                classes(CARD_BODY)
                p {
                    classes(TEXT_SECONDARY, TEXT_CENTER)
                    +Messages.noMonitors()
                }
            }
        } else {
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                table {
                    classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                    thead {
                        tr {
                            th { +Messages.name() }
                            th {
                                classes(TEXT_CENTER)
                                +Messages.status()
                            }
                            columns.forEach { column ->
                                th {
                                    classes(column.headerClasses)
                                    +column.header
                                }
                            }
                            if (!isReadOnlyMode) {
                                // Actions
                                th {}
                            }
                        }
                    }
                    tbody {
                        monitors.forEach { monitor ->
                            monitorListRow(
                                monitor,
                                typeUiConfig,
                                editabilityState,
                                columns,
                                nameTooltip,
                                isReadOnlyMode
                            )
                        }
                    }
                }
            }
        }
    }

private fun <T : MonitorDetailsDto> TBODY.monitorListRow(
    monitor: T,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
    columns: List<MonitorListColumn<T>>,
    nameTooltip: (T) -> String?,
    isReadOnlyMode: Boolean,
) {
    tr {
        testId(typeUiConfig.testId("row"))
        xData(
            """${typeUiConfig.alpineComponent("MonitorListItem")}(
            |${monitor.id},
            |${monitor.enabled},
            |${monitor.statusPages.isNotEmpty()},
            |${Messages.clonedMonitorName(monitor.name).asJsonString()}
            |)
            """.trimMargin()
        )
        monitorNameCell(monitor, typeUiConfig, nameTooltip(monitor))
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
        if (!isReadOnlyMode) {
            monitorListRowActions(monitor, typeUiConfig, editabilityState)
        }
    }
}

private fun TR.monitorListRowActions(
    monitor: MonitorDetailsDto,
    typeUiConfig: MonitorTypeUiConfig,
    editabilityState: AppGlobals.EditabilityState,
) {
    val deleteModalId = "delete-monitor-modal-${monitor.id}"
    td {
        classes(TEXT_CENTER)
        div {
            classes(FLEX_NOWRAP, BTN_GROUP)
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
        // Delete modal
        val isDeleteDisabled = monitor.statusPages.isNotEmpty() && editabilityState.areStatusPagesReadOnly()
        deleteMonitorModal(
            modalId = deleteModalId,
            monitorName = monitor.name,
            isDeleteDisabled = isDeleteDisabled,
        )
    }
}
