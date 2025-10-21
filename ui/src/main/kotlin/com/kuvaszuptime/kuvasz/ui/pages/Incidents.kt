package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import java.time.Duration

fun renderIncidentsPage(globals: AppGlobals, period: Duration, incidents: List<IncidentDto>): String {
    val formattedPeriod = period.formatAsSimpleInterval()
    return withLayout(
        globals,
        title = Messages.incidentsInTheLast(formattedPeriod),
        pageTitle = { incidentsPageHeader(formattedPeriod, selectedPeriod = period) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
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
    }
}

private fun FlowContent.monitorTypeBadge(incidentType: IncidentType) {
    val icon = when (incidentType) {
        IncidentType.HTTP -> Icon.WORLD
        IncidentType.SSL -> Icon.LOCK_OPEN
        IncidentType.PUSH -> Icon.HEARTBEAT
    }
    val label = when (incidentType) {
        IncidentType.HTTP -> "HTTP"
        IncidentType.SSL -> "SSL"
        IncidentType.PUSH -> "Push"
    }
    val colorClasses = when (incidentType) {
        IncidentType.HTTP -> mutableSetOf(BG_BLUE_LT, TEXT_BLUE_LT_FG)
        IncidentType.SSL -> mutableSetOf(BG_YELLOW_LT, TEXT_YELLOW_LT_FG)
        IncidentType.PUSH -> mutableSetOf(BG_RED_LT, TEXT_RED_LT_FG)
    }
    span {
        classes(colorClasses.plus(STATUS))
        tooltip(label)
        icon(icon)
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

private fun IncidentDto.getMonitorUrl(): String = when (this.incidentType) {
    IncidentType.HTTP -> "/http-monitors/${this.monitorId}"
    IncidentType.SSL -> "/http-monitors/${this.monitorId}#http-monitor-details-ssl-events"
    IncidentType.PUSH -> "/push-monitors/${this.monitorId}"
}

private fun HtmlBlockTag.incidentsPageHeader(formattedPeriod: String, selectedPeriod: Duration) {
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
                            span {
                                classes(BADGE)
                                +Messages.lastX(formattedPeriod)
                            }
                        }
                    }
                }
            }
            // Period selector
            div {
                classes(COL_SM_AUTO, MS_AUTO)
                div {
                    @Suppress("MagicNumber")
                    periodSelector(
                        selected = selectedPeriod,
                        options = listOf(
                            Duration.ofHours(1),
                            Duration.ofHours(6),
                            Duration.ofHours(12),
                            Duration.ofDays(1),
                            Duration.ofDays(7),
                            Duration.ofDays(30),
                        )
                    )
                }
            }
        }
    }
}

private fun FlowContent.periodSelector(options: List<Duration>, selected: Duration) {
    select {
        classes(FORM_SELECT)
        onChange = "{window.location = '/incidents?period=' + this.value;}"
        options.forEach { duration ->
            val formattedPeriod = duration.formatAsSimpleInterval()
            option {
                value = duration.toString()
                this.selected = selected.equals(duration)
                +formattedPeriod
            }
        }
    }
}
