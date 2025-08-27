package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.durationBetween
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderHttpUptimeEvents(events: List<HttpUptimeEventDto>): String =
    buildString { appendHTML().div { detailsUptimeEvents(events) } }

internal fun FlowContent.detailsUptimeEvents(events: List<HttpUptimeEventDto>) {
    div {
        classes(COL_12)
        div {
            classes(CARD)
            div {
                classes(CARD_TABLE, TABLE_RESPONSIVE)
                table {
                    classes(CSSClass.TABLE)
                    thead {
                        tr {
                            th { +Messages.status() }
                            th { +Messages.startedAt() }
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL)
                                +Messages.duration()
                            }
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL)
                                +Messages.details()
                            }
                        }
                    }
                    tbody {
                        events.forEach { event ->
                            tr {
                                td { uptimeStatusOfEvent(event) }
                                td {
                                    classes(TEXT_NOWRAP)
                                    +event.startedAt.toDateTimeString()
                                }
                                td {
                                    classes(TEXT_NOWRAP, D_NONE, D_MD_TABLE_CELL)
                                    +event.startedAt.durationBetween(event.endedAt ?: event.updatedAt)
                                }
                                td {
                                    classes(TEXT_WRAP, D_NONE, D_MD_TABLE_CELL)
                                    +event.error.orEmpty()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
