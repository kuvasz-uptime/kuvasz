package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderSSLEvents(isSSLCheckEnabled: Boolean, events: List<SSLEventDto>): String =
    buildString { appendHTML().div { detailsSSLEvents(isSSLCheckEnabled, events) } }

internal fun FlowContent.detailsSSLEvents(isSSLCheckEnabled: Boolean, events: List<SSLEventDto>) {
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
                            th { +Messages.duration() }
                            th {
                                classes(D_NONE, D_MD_TABLE_CELL)
                                +Messages.details()
                            }
                        }
                    }
                    tbody {
                        events.forEach { event ->
                            tr {
                                td { sslStatusOfEvent(event) }
                                td {
                                    classes(TEXT_NOWRAP)
                                    +event.startedAt.toDateTimeString()
                                }
                                td {
                                    +getDurationOfEvent(
                                        isMonitorEnabled = isSSLCheckEnabled,
                                        startedAt = event.startedAt,
                                        endedAt = event.endedAt,
                                        updatedAt = event.updatedAt,
                                    ).formatAsInterval()
                                }
                                td {
                                    classes(TEXT_WRAP, D_NONE, D_MD_TABLE_CELL)
                                    when {
                                        event.error != null -> +Messages.reasonExplanation(event.error.orEmpty())
                                        event.sslValidUntil != null ->
                                            +Messages.validUntil(event.sslValidUntil?.toDateTimeString().orEmpty())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
