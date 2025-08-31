package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.IncidentDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderIncidents(incidents: List<IncidentDto>): String =
    buildString { appendHTML().div { detailsIncidentList(incidents) } }

internal fun FlowContent.detailsIncidentList(incidents: List<IncidentDto>) {
    div {
        classes(COL_12)
        div {
            classes(CARD)
            if (incidents.isNotEmpty()) {
                div {
                    classes(CARD_TABLE, TABLE_RESPONSIVE)
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
                        +Messages.noIncidentsInPeriod()
                    }
                }
            }
        }
    }
}
