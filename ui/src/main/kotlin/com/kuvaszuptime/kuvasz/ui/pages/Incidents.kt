package com.kuvaszuptime.kuvasz.ui.pages

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.monitorType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val INCIDENT_LIST_ID = "incident-list"
private const val INCIDENT_COUNT_ID = "incident-count"
private const val REFRESH_INCIDENT_LIST_EVENT = "refresh-incident-list"

fun renderIncidentsPage(globals: AppGlobals, period: Duration, selectedType: IncidentType?): String =
    withLayout(
        globals,
        title = Messages.incidents(),
        pageTitle = { incidentsPageHeader() }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    incidentFilterToolbar(selectedPeriod = period, selectedType = selectedType)
                    div {
                        hx {
                            get(incidentListFragmentPath(period, selectedType))
                            trigger {
                                load()
                                event(REFRESH_INCIDENT_LIST_EVENT)
                                every(15.seconds)
                            }
                            onSwapReinitTooltips()
                        }
                        id = INCIDENT_LIST_ID
                        htmxLoadingIndicator()
                    }
                }
            }
        }
    }

/**
 * The table of the incidents page, refreshed by htmx, along with the incident count of the filter toolbar swapped out
 * of band.
 */
fun renderIncidentList(incidents: List<IncidentDto>): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        if (incidents.isEmpty()) {
            div {
                incidentCountLabel(count = 0)
                emptyState(
                    icon = Icon.FLAME,
                    title = Messages.noIncidents(),
                    subtitle = Messages.noIncidentsInPeriod(),
                )
            }
        } else {
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                incidentCountLabel(incidents.size)
                table {
                    classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                    thead {
                        tr {
                            // Status
                            th {
                                classes(TEXT_CENTER)
                                +Messages.status()
                            }
                            // Monitor name
                            th { +Messages.monitor() }
                            // Type
                            th {
                                classes(TEXT_CENTER)
                                +Messages.type()
                            }
                            // Started at
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL, PX_3)
                                +Messages.startedAt()
                            }
                            // Duration
                            th {
                                classes(PX_3, D_NONE, D_SM_TABLE_CELL)
                                +Messages.duration()
                            }
                            // Details
                            th {
                                classes(D_NONE, D_LG_TABLE_CELL, PX_3)
                                +Messages.details()
                            }
                            // Actions
                            th {}
                        }
                    }
                    tbody {
                        incidents.forEach { incident ->
                            tr {
                                // Status
                                td {
                                    classes(TEXT_CENTER)
                                    incidentStatusBadge(incident)
                                }
                                // Monitor name
                                td {
                                    classes(TEXT_WRAP, TEXT_BREAK)
                                    a(href = incident.getMonitorUrl()) {
                                        classes(TEXT_RESET)
                                        +incident.monitorName.abbreviate(MONITOR_NAME_MAX_LENGTH)
                                    }
                                }
                                // Type
                                td {
                                    classes(TEXT_CENTER)
                                    span {
                                        monitorTypeBadge(incident.incidentType)
                                    }
                                }
                                // Started at
                                td {
                                    classes(TEXT_NOWRAP, D_NONE, D_MD_TABLE_CELL, PX_3)
                                    +incident.startedAt.toDateTimeString()
                                }
                                // Duration
                                td {
                                    classes(TEXT_NOWRAP, PX_3, D_NONE, D_SM_TABLE_CELL)
                                    +getDurationOfEvent(
                                        isMonitorEnabled = incident.isMonitorEnabled,
                                        startedAt = incident.startedAt,
                                        endedAt = incident.endedAt,
                                        updatedAt = incident.updatedAt,
                                    ).formatAsInterval()
                                }
                                // Details
                                td {
                                    classes(TEXT_WRAP, TEXT_BREAK, D_NONE, D_LG_TABLE_CELL, PX_3)
                                    +incident.details.orEmpty()
                                }
                                // Actions
                                td {
                                    classes(TEXT_END)
                                    incidentDetailsButton(incident)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private fun incidentListFragmentPath(period: Duration, type: IncidentType?): String =
    "/incidents/fragments/list?period=$period" + type?.let { "&type=${it.name.lowercase()}" }.orEmpty()

private fun FlowContent.incidentCountLabel(count: Int?) {
    span {
        id = INCIDENT_COUNT_ID
        classes(TEXT_SECONDARY)
        testId(INCIDENT_COUNT_ID)
        if (count != null) {
            hx { swapOob() }
            +Messages.incidentListCount(count)
        }
    }
}

internal val IncidentType.label: String
    get() = when (this) {
        IncidentType.HTTP -> "HTTP"
        IncidentType.SSL -> "SSL"
        IncidentType.PUSH -> "Push"
        IncidentType.ICMP -> "ICMP"
        IncidentType.TCP -> "TCP"
        IncidentType.DNS -> "DNS"
        IncidentType.DOCKER -> "Docker"
    }

internal val IncidentType.icon: Icon
    get() = if (this == IncidentType.SSL) Icon.LOCK_OPEN else MonitorTypeUiConfig.of(monitorType).icon

internal val IncidentType.color: Color
    get() = if (this == IncidentType.SSL) Color.YELLOW_LT else MonitorTypeUiConfig.of(monitorType).color

internal fun FlowContent.monitorTypeBadge(incidentType: IncidentType) {
    span {
        classes(STATUS, incidentType.color.bgColor, incidentType.color.textColor)
        tooltip(incidentType.label)
        icon(incidentType.icon)
    }
}

fun FlowContent.incidentStatusBadge(incident: IncidentDto) {
    span {
        classes {
            mutableSetOf(STATUS).apply {
                if (incident.status == IncidentStatus.RESOLVED) add(STATUS_GREEN) else add(STATUS_RED)
            }
        }
        val tooltipText = if (incident.status == IncidentStatus.RESOLVED) {
            Messages.resolvedDetails(incident.details.orEmpty())
        } else {
            Messages.ongoingDetails(incident.details.orEmpty())
        }
        tooltip(tooltipText)
        span {
            classes(STATUS_DOT)
        }
    }
}

internal fun incidentDetailsPath(incidentType: IncidentType, eventId: Long): String =
    "/incidents/${incidentType.name.lowercase()}/$eventId"

internal val IncidentDto.detailsPath: String
    get() = incidentDetailsPath(incidentType, eventId)

/** The link of an incident list row to the details page of the incident. */
internal fun FlowContent.incidentDetailsButton(incident: IncidentDto) {
    a(href = incident.detailsPath) {
        classes(BTN, BTN_SM, BTN_ICON)
        testId("incident-details-button")
        tooltip(Messages.viewIncidentDetails())
        ariaLabel(Messages.viewIncidentDetails())
        icon(Icon.EYE)
    }
}

internal fun IncidentDto.getMonitorUrl(): String = if (incidentType == IncidentType.SSL) {
    sslEventsPath(monitorId)
} else {
    MonitorTypeUiConfig.of(incidentType.monitorType).detailsPath(monitorId)
}

private fun FlowContent.incidentFilterToolbar(selectedPeriod: Duration, selectedType: IncidentType?) {
    div {
        classes(CARD_HEADER, PY_2)
        testId("incident-filters")
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER, W_100)
            div {
                classes(CSSClass.COL)
                incidentCountLabel(count = null)
            }
            div {
                classes(COL_12, COL_SM_AUTO, MS_SM_AUTO)
                div {
                    classes(INPUT_ICON)
                    span {
                        classes(INPUT_ICON_ADDON)
                        icon(Icon.CLOCK)
                    }
                    periodSelector(selected = selectedPeriod) {
                        classes(FORM_SELECT, FORM_SELECT_SM)
                        ariaLabel(Messages.filterByPeriod())
                        testId("incident-period-filter")
                        onChange = "applyIncidentFilter('period', this.value)"
                    }
                }
            }
            div {
                classes(COL_12, COL_SM_AUTO)
                div {
                    classes(INPUT_ICON)
                    span {
                        classes(INPUT_ICON_ADDON)
                        icon(Icon.FLAME)
                    }
                    select {
                        classes(FORM_SELECT, FORM_SELECT_SM)
                        ariaLabel(Messages.filterByIncidentType())
                        testId("incident-type-filter")
                        onChange = "applyIncidentFilter('type', this.value)"
                        // An absent parameter means every type, so the unfiltered page keeps its plain URL
                        option {
                            value = ""
                            selected = selectedType == null
                            +Messages.allIncidentTypes()
                        }
                        IncidentType.entries.forEach { type ->
                            option {
                                value = type.name.lowercase()
                                selected = selectedType == type
                                +type.label
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun HtmlBlockTag.incidentsPageHeader() {
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
                            +Messages.incidents()
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            compactIconButton(Icon.REFRESH, onClick = "refreshIncidentList()") {
                                testId("incident-list-refresh-button")
                            }
                        }
                    }
                }
            }
        }
    }
}
