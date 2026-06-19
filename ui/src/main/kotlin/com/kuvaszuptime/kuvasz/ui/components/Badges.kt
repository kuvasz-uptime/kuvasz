package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
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

internal fun FlowContent.uptimeBadgeOfStatus(uptimeStatus: UptimeStatus?): Unit =
    if (uptimeStatus != null) {
        span {
            val classes = mutableSetOf(STATUS)
                .addIf(uptimeStatus == UptimeStatus.UP, STATUS_GREEN, STATUS_RED)
            classes(classes)
            uptimeStatusLabel(withBadge = true, uptimeStatus.literal)
        }
    } else {
        span {
            classes(STATUS, STATUS_YELLOW)
            uptimeStatusLabel(withBadge = true, Messages.inProgress())
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
    monitor: HttpMonitorDetailsDto,
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

internal fun FlowContent.readOnlyBadge(tooltipText: String) {
    span {
        testId("read-only-badge")
        classes(BADGE, TEXT_BLUE_LT_FG, BG_BLUE_LT, MS_2)
        tooltip(tooltipText)
        icon(Icon.LOCK_COG)
    }
}

internal fun FlowContent.inlineVersionUpdateBadge(versionInfo: VersionInfo) {
    if (!versionInfo.isUpToDate && versionInfo.latestVersionDetails != null) {
        a(href = versionInfo.latestVersionDetails.toString()) {
            targetBlank()
            classes(BADGE, BADGE_SM, BG_GREEN, MS_2, TEXT_GREEN_FG)
            tooltip(Messages.newVersionAvailable(versionInfo.latestVersion.orEmpty()))
            icon(Icon.UPLOAD)
        }
    }
}

internal fun FlowContent.inlineBadge(text: String, color: Color = Color.DEFAULT, tooltip: String? = null) {
    span {
        classes(BADGE, color.bgColor, color.textColor)
        tooltip?.let { tooltip(it) }
        +text
    }
}

internal fun FlowContent.inlineStatusBadge(
    text: String,
    color: Color = Color.DEFAULT,
    icon: Icon? = null,
    tooltip: String? = null
) {
    span {
        classes(STATUS, color.bgColor, color.textColor)
        tooltip?.let { tooltip(it) }
        icon?.let { icon(it) }
        +text
    }
}
