package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import kotlinx.html.stream.*

/** A page of the incidents on the details page of a monitor, see [monitorIncidentListQuery]. */
fun renderIncidents(incidents: PagedResult<IncidentDto>, query: IncidentListQuery): String =
    buildString {
        appendHTML().div {
            // The page shown, which is the last one if the requested page is past the end, see listQuery in kuvasz.js
            attributes["data-list-page"] = "${incidents.page}"
            detailsIncidentList(incidents, query)
        }
    }

private fun FlowContent.detailsIncidentList(incidents: PagedResult<IncidentDto>, query: IncidentListQuery) {
    div {
        classes(COL_12)
        div {
            classes(CARD)
            if (incidents.result.isNotEmpty()) {
                div {
                    classes(TABLE_RESPONSIVE)
                    table {
                        classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                        thead {
                            tr {
                                // Status
                                th {
                                    classes(TEXT_CENTER)
                                    +Messages.status()
                                }
                                // Started at
                                th {
                                    classes(PX_3)
                                    +Messages.startedAt()
                                }
                                // Duration
                                th {
                                    classes(PX_3)
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
                            incidents.result.forEach { incident ->
                                tr {
                                    // Status
                                    td {
                                        classes(TEXT_CENTER)
                                        incidentStatusBadge(incident)
                                    }
                                    // Started at
                                    td {
                                        classes(TEXT_NOWRAP, PX_3)
                                        +incident.startedAt.toDateTimeString()
                                    }
                                    // Duration
                                    td {
                                        classes(TEXT_NOWRAP, PX_3)
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
                listPagination(incidents, query)
            } else {
                div {
                    classes(CARD_BODY)
                    p {
                        classes(TEXT_SECONDARY, TEXT_CENTER)
                        +Messages.noIncidentsInPeriod()
                    }
                }
            }
        }
    }
}
