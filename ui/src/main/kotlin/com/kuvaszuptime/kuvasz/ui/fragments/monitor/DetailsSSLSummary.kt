package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderSSLSummary(monitor: MonitorDetailsDto): String =
    buildString { appendHTML().div { detailsSSLSummary(monitor) } }

private const val SSL_ERROR_MAX_LENGTH = 40

internal fun FlowContent.detailsSSLSummary(monitor: MonitorDetailsDto) {
    div {
        id = "monitor-details-ssl-summary"
        classes(ROW, ROW_CARDS, MB_3)
        hx { swapOob() }

        div {
            classes(COL_MD_4)
            div {
                classes(CARD)
                div {
                    classes(CARD_STATUS_START, getSSLStatusClass(monitor.sslStatus))
                }
                div {
                    classes(CARD_BODY)
                    if (monitor.sslStatusStartedAt != null) {
                        div {
                            classes(SUBHEADER)
                            +when (monitor.sslStatus) {
                                SslStatus.VALID -> Messages.valid()
                                SslStatus.INVALID -> Messages.invalid()
                                SslStatus.WILL_EXPIRE -> Messages.expiresSoon()
                                null -> ""
                            }
                        }
                        h4 {
                            classes(M_0)
                            monitor.sslError?.let { error ->
                                tooltip(title = error, location = TooltipLocation.RIGHT)
                            }
                            +when (monitor.sslStatus) {
                                SslStatus.VALID -> Messages.untilDatetime(
                                    monitor.sslValidUntil?.toDateTimeString().orEmpty()
                                )

                                SslStatus.INVALID -> Messages.reasonExplanation(
                                    monitor.sslError?.abbreviate(SSL_ERROR_MAX_LENGTH).orEmpty()
                                )

                                SslStatus.WILL_EXPIRE -> Messages.atDatetime(
                                    monitor.sslValidUntil?.toDateTimeString().orEmpty()
                                )

                                null -> ""
                            }
                        }
                    } else {
                        div {
                            classes(SUBHEADER)
                            +Messages.sslStatusNotAvailable()
                        }
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
                        +Messages.lastCheck()
                    }
                    h4 {
                        classes(M_0)
                        monitor.lastSSLCheck?.let { +it.timeAgo() }
                            ?: +Messages.waitingForCheck()
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
                    h4 {
                        classes(M_0)
                        +if (monitor.enabled) {
                            monitor.nextSSLCheck?.timeAgo()
                                ?: Messages.waitingForCheck()
                        } else {
                            Messages.monitorIsPaused()
                        }
                    }
                }
            }
        }
    }
}

private fun getSSLStatusClass(status: SslStatus?): CSSClass =
    when (status) {
        SslStatus.WILL_EXPIRE -> BG_WARNING
        SslStatus.VALID -> BG_SUCCESS
        SslStatus.INVALID -> BG_DANGER
        null -> BG_ORANGE
    }
