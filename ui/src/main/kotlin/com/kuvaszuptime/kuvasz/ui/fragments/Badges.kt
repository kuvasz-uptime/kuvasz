package com.kuvaszuptime.kuvasz.ui.fragments

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.uptimeBadgeOfMonitor(monitor: MonitorDetailsDto, withTooltip: Boolean) {
    return when {
        monitor.enabled && monitor.uptimeStatus != null -> {
            span {
                val classes = mutableSetOf(STATUS)
                    .addIf(monitor.uptimeStatus == UptimeStatus.UP, STATUS_GREEN, STATUS_RED)
                classes(classes)
                if (withTooltip && monitor.uptimeStatus == UptimeStatus.DOWN) {
                    tooltip(title = monitor.uptimeError ?: Messages.unknownError())
                }
                uptimeStatusLabel(withBadge = true, monitor.uptimeStatus?.literal.orEmpty())
            }
        }

        monitor.enabled && monitor.uptimeStatus == null -> {
            span {
                classes(STATUS, STATUS_YELLOW)
                uptimeStatusLabel(withBadge = true, Messages.inProgress())
            }
        }

        !monitor.enabled -> {
            span {
                classes(setOf(STATUS, STATUS_CYAN))
                uptimeStatusLabel(withBadge = true, Messages.paused(), animated = false)
            }
        }

        else -> {}
    }
}

internal fun FlowContent.uptimeStatusOfMonitor(
    monitor: MonitorDetailsDto,
    withTooltip: Boolean
) {
    return when {
        monitor.enabled && monitor.uptimeStatus != null -> {
            span {
                val classes = mutableSetOf(STATUS_INDICATOR, STATUS_INDICATOR_ANIMATED)
                    .addIf(monitor.uptimeStatus == UptimeStatus.UP, STATUS_GREEN, STATUS_RED)
                classes(classes)
                if (withTooltip && monitor.uptimeStatus == UptimeStatus.DOWN) {
                    tooltip(title = monitor.uptimeError ?: Messages.unknownError())
                }
                uptimeStatusLabel(withBadge = false, monitor.uptimeStatus?.literal.orEmpty())
            }
        }

        monitor.enabled && monitor.uptimeStatus == null -> {
            span {
                classes(STATUS_INDICATOR, STATUS_YELLOW, STATUS_INDICATOR_ANIMATED)
                uptimeStatusLabel(withBadge = false, Messages.inProgress())
            }
        }

        !monitor.enabled -> {
            span {
                classes(STATUS_INDICATOR, STATUS_CYAN)
                uptimeStatusLabel(withBadge = false, Messages.paused(), animated = false)
            }
        }

        else -> {}
    }
}

internal fun FlowContent.uptimeStatusOfEvent(event: UptimeEventDto) =
    span {
        classes {
            mutableSetOf(STATUS).apply {
                if (event.status == UptimeStatus.UP) add(STATUS_GREEN) else add(STATUS_RED)
            }
        }
        event.error?.let { tooltip(title = it) }
        span {
            classes(STATUS_DOT)
        }
        span {
            classes(D_NONE, D_MD_INLINE)
            +event.status.literal
        }
    }

private fun FlowContent.uptimeStatusLabel(
    withBadge: Boolean,
    label: String,
    animated: Boolean = true
) {
    if (withBadge) {
        span {
            val classes = mutableSetOf(STATUS_DOT).addIf(animated, STATUS_DOT_ANIMATED)
            classes(classes)
        }
        span {
            classes(D_NONE, D_MD_INLINE)
            +label
        }
    } else {
        @Suppress("MagicNumber")
        repeat(3) {
            span { classes(STATUS_INDICATOR_CIRCLE) }
        }
    }
}

internal fun FlowContent.sslStatusOfMonitor(
    monitor: MonitorDetailsDto,
    withTooltip: Boolean
) {
    if (monitor.enabled && monitor.sslCheckEnabled) {
        when (monitor.sslStatus) {
            SslStatus.VALID -> span {
                classes(STATUS, STATUS_GREEN)
                if (withTooltip) {
                    tooltip(
                        title = Messages.validUntil(monitor.sslValidUntil?.toDateTimeString().orEmpty())
                    )
                }
                icon(Icon.LOCK_CLOSED)
            }

            SslStatus.INVALID -> span {
                classes(STATUS, STATUS_RED)
                if (withTooltip) {
                    tooltip(title = Messages.invalid())
                }
                icon(Icon.LOCK_OPEN)
            }

            SslStatus.WILL_EXPIRE -> span {
                classes(STATUS, STATUS_YELLOW)
                if (withTooltip) {
                    tooltip(
                        title = Messages.expiresSoonValidUntil(
                            monitor.sslValidUntil?.toDateTimeString().orEmpty()
                        )
                    )
                }
                icon(Icon.TIMER)
            }

            else -> span {
                classes(STATUS, STATUS_ORANGE)
                if (withTooltip) {
                    tooltip(title = Messages.waitingForCheck())
                }
                icon(Icon.LOCK_QUESTION)
            }
        }
    } else {
        span {
            classes(STATUS, STATUS_GRAY)
            if (withTooltip) {
                tooltip(Messages.disabled())
            }
            icon(Icon.LOCK_OFF)
        }
    }
}

internal fun FlowContent.sslStatusOfEvent(event: SSLEventDto) {
    span {
        classes(STATUS, event.status.toStatusClass())
        event.error?.let { tooltip(it) }
        span {
            classes(STATUS_DOT)
        }
        span {
            classes(D_NONE, D_MD_INLINE)
            +event.status.renderLabel()
        }
    }
}

private fun SslStatus.toStatusClass(): CSSClass =
    when (this) {
        SslStatus.VALID -> STATUS_GREEN
        SslStatus.WILL_EXPIRE -> STATUS_YELLOW
        SslStatus.INVALID -> STATUS_RED
    }

private fun SslStatus.renderLabel(): String = when (this) {
    SslStatus.VALID -> Messages.valid()
    SslStatus.WILL_EXPIRE -> Messages.expiresSoon()
    SslStatus.INVALID -> Messages.invalid()
}
