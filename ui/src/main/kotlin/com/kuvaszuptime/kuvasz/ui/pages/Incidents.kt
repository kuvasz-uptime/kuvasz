package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.monitorType
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import kotlinx.html.stream.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val INCIDENT_LIST_ID = "incident-list"
private const val INCIDENT_LIST_PATH = "/incidents"
private const val REFRESH_INCIDENT_LIST_EVENT = "refresh-incident-list"
private const val TYPE_PARAM = "type"
private const val MONITOR_ID_PARAM = "monitorId"
private val INCIDENT_LIST_REFRESH_INTERVAL = 30.seconds

/**
 * The filters of the incident list, named after the query parameters of the v3 API, except for [type], which is a
 * path parameter there. Its values are lowercase, the way they appear in the URLs of the incidents.
 */
class IncidentListFilters internal constructor(params: QueryParams) : ListFilters {

    val name: TextFilter = textFilter(params, "name", Messages.monitor(), placeholder = Messages.searchByMonitorName())

    val type: ChoiceFilter<IncidentType> = choiceFilter(
        params,
        TYPE_PARAM,
        Messages.type(),
        anyLabel = Messages.allIncidentTypes(),
        options = IncidentType.entries.map { FilterOption(it, queryValue = it.queryValue, label = it.label) },
    )

    val period: DefaultedChoiceFilter<Duration> = periodFilter(params)

    val includeResolved: DefaultedChoiceFilter<Boolean> = yesNoOptions().let { options ->
        defaultedChoiceFilter(
            params,
            "includeResolved",
            Messages.includeResolvedIncidents(),
            options,
            default = options.first { it.value },
        )
    }

    val monitorId: NumberFilter = numberFilter(params, MONITOR_ID_PARAM, Messages.monitorIdLabel())

    override val all: List<ListFilter> = listOf(name, type, period, includeResolved, monitorId)
}

private val IncidentType.queryValue: String get() = name.lowercase()

// A period is matched by its length, so e.g. P7D is the same as PT168H. Any other positive one (e.g. from an older
// link) is added to the options, so it's kept instead of being replaced by the default.
private fun periodFilter(params: QueryParams): DefaultedChoiceFilter<Duration> {
    val requested = params("period").firstOrNull()
        ?.let { runCatching { Duration.parse(it) }.getOrNull() }
        ?.takeIf { it.isPositive }
    val options = (UIDefaults.PERIOD_SELECTOR_OPTIONS + listOfNotNull(requested)).distinct().sorted().map {
        FilterOption(it, queryValue = it.toString(), label = it.formatAsSimpleInterval())
    }
    val default = options.first { it.value == ListDefaults.INCIDENT_PERIOD }
    return DefaultedChoiceFilter(
        param = "period",
        label = Messages.periodLabel(),
        options = options,
        default = default,
        selected = options.firstOrNull { it.value == requested } ?: default,
    )
}

typealias IncidentListQuery = ListQuery<IncidentSortField, IncidentListFilters>

/** The state of the incident list, parsed from the query parameters of its page or fragment. */
fun incidentListQuery(params: QueryParams): IncidentListQuery = ListQuery.parse(
    params = params,
    path = INCIDENT_LIST_PATH,
    filters = IncidentListFilters(params),
    sortFields = IncidentSortField.entries,
    defaultSorting = ListDefaults.INCIDENT_SORTING,
)

/**
 * The state of the incident list on the details page of a monitor: its incidents of [type], with every other filter
 * and the sorting left at their defaults, so only the page and its size are taken from [params]. Its URLs lead to the
 * incidents page, filtered the same way.
 */
fun monitorIncidentListQuery(params: QueryParams, type: IncidentType, monitorId: Long): IncidentListQuery =
    incidentListQuery { name ->
        when (name) {
            TYPE_PARAM -> listOf(type.queryValue)
            MONITOR_ID_PARAM -> listOf("$monitorId")
            ListQuery.PAGE_PARAM, ListQuery.SIZE_PARAM -> params(name)
            else -> emptyList()
        }
    }

/** The path the incident list of [monitorIncidentListQuery] is loaded from. */
internal fun monitorIncidentListFragmentPath(type: IncidentType, monitorId: Long): String =
    "$INCIDENT_LIST_PATH/fragments/monitor/${type.queryValue}/$monitorId"

fun renderIncidentsPage(globals: AppGlobals, query: IncidentListQuery): String =
    withLayout(
        globals,
        title = Messages.incidents(),
        pageTitle = { incidentsPageHeader() }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                pagedListCard(
                    query = query,
                    listElementId = INCIDENT_LIST_ID,
                    fragmentPath = "$INCIDENT_LIST_PATH/fragments/list",
                    refreshEvent = REFRESH_INCIDENT_LIST_EVENT,
                    refreshInterval = INCIDENT_LIST_REFRESH_INTERVAL,
                )
            }
        }
    }

/** The table of the incidents page, a page of the incidents matching [query], refreshed by htmx. */
fun renderIncidentList(incidents: PagedResult<IncidentDto>, query: IncidentListQuery): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        div {
            // The page shown, which is the last one if the requested page is past the end, see listQuery in kuvasz.js
            attributes["data-list-page"] = "${incidents.page}"
            activeFilterCount(INCIDENT_LIST_ID, query.activeFilterCount, outOfBand = true)
            when {
                incidents.result.isNotEmpty() -> {
                    incidentTable(incidents.result, query)
                    listPagination(incidents, query)
                }

                query.activeFilterCount > 0 -> emptyState(
                    icon = Icon.FILTER,
                    title = Messages.noMatchingIncidents(),
                    subtitle = Messages.noMatchingIncidentsDescription(),
                )

                else -> emptyState(
                    icon = Icon.FLAME,
                    title = Messages.noIncidents(),
                    subtitle = Messages.noIncidentsInPeriod(),
                )
            }
        }
    }

private fun FlowContent.incidentTable(incidents: List<IncidentDto>, query: IncidentListQuery) {
    div {
        classes(TABLE_RESPONSIVE)
        table {
            classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
            thead {
                tr {
                    sortableHeader(query, IncidentSortField.STATUS, Messages.status(), setOf(TEXT_CENTER))
                    sortableHeader(query, IncidentSortField.MONITOR_NAME, Messages.monitor())
                    sortableHeader(query, IncidentSortField.INCIDENT_TYPE, Messages.type(), setOf(TEXT_CENTER))
                    sortableHeader(
                        query,
                        IncidentSortField.STARTED_AT,
                        Messages.startedAt(),
                        setOf(D_NONE, D_MD_TABLE_CELL, PX_3),
                    )
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
                incidents.forEach { incident -> incidentRow(incident) }
            }
        }
    }
}

private fun TBODY.incidentRow(incident: IncidentDto) {
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

internal val IncidentDto.detailsPath: String
    get() = "/incidents/${incidentType.name.lowercase()}/$eventId"

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
