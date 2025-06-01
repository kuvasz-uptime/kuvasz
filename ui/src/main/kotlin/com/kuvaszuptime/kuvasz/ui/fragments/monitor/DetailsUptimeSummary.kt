package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.durationBetween
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderUptimeSummary(monitor: MonitorDetailsDto): String =
    buildString { appendHTML().div { detailsUptimeSummary(monitor) } }

fun FlowContent.detailsUptimeSummary(monitor: MonitorDetailsDto) {
    div {
        id = "monitor-details-uptime-summary"
        classes(ROW, ROW_CARDS, MB_3)
        hx { swapOob() }

        div {
            classes(COL_MD_4)
            div {
                classes(CARD)
                div {
                    classes(
                        mutableSetOf(CARD_STATUS_START).addIfNotNull(getCardStatusClass(monitor))
                    )
                }
                div {
                    classes(CARD_BODY)
                    if (monitor.enabled) {
                        monitor.uptimeStatusStartedAt?.let { uptimeStatusStartedAt ->
                            div {
                                classes(SUBHEADER)
                                +Messages.currentlyFor(monitor.uptimeStatus?.literal.orEmpty())
                            }
                            h4 {
                                classes(M_0)
                                +uptimeStatusStartedAt.durationBetween()
                            }
                        } ?: run {
                            div {
                                classes(SUBHEADER)
                                +Messages.monitorWasJustCreated()
                            }
                            h4 {
                                classes(M_0)
                                +Messages.waitingForCheck()
                            }
                        }
                    } else {
                        div {
                            classes(SUBHEADER)
                            +Messages.currentUptimeStatus()
                        }
                        h4 {
                            classes(M_0)
                            +Messages.monitorIsPaused()
                        }
                    }
                }
            }
        }

        div {
            classes(COL_MD_4)
            div {
                classes(CARD)
                div {
                    classes(CARD_BODY)
                    div {
                        classes(SUBHEADER)
                        +Messages.lastCheck()
                    }
                    monitor.lastUptimeCheck?.let { lastUptimeCheck ->
                        h4 {
                            classes(M_0)
                            +lastUptimeCheck.timeAgo()
                        }
                    } ?: run {
                        h4 {
                            classes(M_0)
                            +Messages.waitingForCheck()
                        }
                    }
                }
            }
        }

        div {
            classes(COL_MD_4)
            div {
                classes(CARD)
                div {
                    classes(CARD_BODY)
                    div {
                        classes(SUBHEADER)
                        +Messages.nextCheck()
                    }

                    if (monitor.enabled) {
                        h4 {
                            classes(M_0)
                            +monitor.nextUptimeCheck?.timeAgo().orEmpty()
                        }
                    } else {
                        h4 {
                            classes(M_0)
                            +Messages.monitorIsPaused()
                        }
                    }
                }
            }
        }
    }
}

private fun getCardStatusClass(monitor: MonitorDetailsDto): CSSClass? {
    return when {
        !monitor.enabled -> BG_CYAN
        monitor.uptimeStatus == null -> BG_WARNING
        monitor.uptimeStatus == UptimeStatus.UP -> BG_SUCCESS
        monitor.uptimeStatus == UptimeStatus.DOWN -> BG_DANGER
        else -> null
    }
}
