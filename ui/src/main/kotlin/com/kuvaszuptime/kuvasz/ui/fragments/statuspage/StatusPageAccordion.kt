package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The accordions below the overall status of a status page, in the order they are rendered. The [key] identifies them
 * in `kuvasz.js`, which keeps the visitor's choice about them across the refreshes of the page, see
 * `applyStatusPageAccordionsState()`.
 */
internal enum class StatusPageAccordion(
    val key: String,
    val isOpenByDefault: Boolean,
    val icon: Icon,
    val iconColor: CSSClass,
    val statusColor: CSSClass,
    val title: String,
) {
    ACTIVE_INCIDENTS(
        "active-incidents",
        isOpenByDefault = true,
        icon = Icon.FLAME,
        iconColor = TEXT_RED,
        statusColor = BG_DANGER,
        title = Messages.statusPageActiveIncidents(),
    ),
    ACTIVE_MAINTENANCE(
        "active-maintenance",
        isOpenByDefault = true,
        icon = Icon.TOOL,
        iconColor = TEXT_YELLOW,
        statusColor = BG_YELLOW,
        title = Messages.statusPageActiveMaintenanceTitle(),
    ),
    RESOLVED_INCIDENTS(
        "resolved-incidents",
        isOpenByDefault = false,
        icon = Icon.FLAME_OFF,
        iconColor = TEXT_GREEN,
        statusColor = BG_SUCCESS,
        title = Messages.statusPageResolvedIncidents(),
    );

    val elementId: String = "status-page-$key"
    val collapseId: String = "$elementId-collapse"
}

/**
 * The ongoing incidents, the active maintenance windows, and the incidents resolved within the last day, each in its
 * own accordion, which is only there if it has anything to show.
 */
internal fun FlowContent.statusPageAccordions(pageData: StatusPageDataDto) {
    val incidentsByMonitor = pageData.incidentsByMonitor()
    val activeIncidents = incidentsByMonitor.ongoing()
    val maintenanceWindows = pageData.activeMaintenanceWindows
    val resolvedIncidents = incidentsByMonitor.recentlyResolved(pageData.generatedAt)
    if (activeIncidents.isEmpty() && maintenanceWindows.isEmpty() && resolvedIncidents.isEmpty()) return

    div {
        classes(CONTAINER_XL, MB_4)
        statusPageAccordion(StatusPageAccordion.ACTIVE_INCIDENTS, activeIncidents.sumOf { it.second.size }) {
            activeIncidents.forEach { (monitor, incidents) ->
                monitorIncidentsCard(monitor, incidents, isOngoing = true)
            }
        }
        statusPageAccordion(StatusPageAccordion.ACTIVE_MAINTENANCE, maintenanceWindows.size) {
            maintenanceWindows.forEach { window -> maintenanceWindowCard(window) }
        }
        statusPageAccordion(StatusPageAccordion.RESOLVED_INCIDENTS, resolvedIncidents.sumOf { it.second.size }) {
            resolvedIncidents.forEach { (monitor, incidents) ->
                monitorIncidentsCard(monitor, incidents, isOngoing = false)
            }
        }
    }
}

private fun FlowContent.statusPageAccordion(accordion: StatusPageAccordion, count: Int, body: DIV.() -> Unit) {
    if (count == 0) return
    val isOpen = accordion.isOpenByDefault
    div {
        classes(CARD, MB_3)
        testId(accordion.elementId)
        div {
            id = accordion.elementId
            classes(ACCORDION, ACCORDION_FLUSH)
            div {
                classes(ACCORDION_ITEM)
                h2 {
                    classes(ACCORDION_HEADER)
                    button(type = ButtonType.button) {
                        classes(mutableSetOf(ACCORDION_BUTTON, PX_3).addIf(!isOpen, COLLAPSED))
                        testId("${accordion.elementId}-toggle")
                        collapseToggler()
                        dataBsTarget("#${accordion.collapseId}")
                        ariaExpanded(isOpen)
                        ariaControls(accordion.collapseId)
                        span {
                            classes(accordion.iconColor, ME_2)
                            icon(accordion.icon)
                        }
                        +accordion.title
                        span {
                            classes(BADGE, MS_2)
                            testId("${accordion.elementId}-count")
                            +count.toString()
                        }
                        div {
                            classes(ACCORDION_BUTTON_TOGGLE)
                            icon(Icon.CHEVRON_DOWN)
                        }
                    }
                }
                div {
                    id = accordion.collapseId
                    classes(mutableSetOf(ACCORDION_COLLAPSE, COLLAPSE).addIf(isOpen, SHOW))
                    attributes["data-status-page-accordion"] = accordion.key
                    div {
                        classes(ACCORDION_BODY, D_FLEX, FLEX_COLUMN, GAP_3, PT_0, PX_1, PX_SM_2, PX_MD_3)
                        body()
                    }
                }
            }
        }
    }
}
