package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*

internal fun getUptimeCardStatusClass(monitor: MonitorDetailsDto): CSSClass? =
    if (!monitor.enabled) {
        BG_CYAN
    } else {
        monitor.uptimeStatus.cardStatusClass()
    }

internal fun UptimeStatus?.cardStatusClass(): CSSClass =
    when (this) {
        UptimeStatus.UP -> BG_SUCCESS
        UptimeStatus.DOWN -> BG_DANGER
        null -> BG_WARNING
    }

internal fun getUptimeCardIcon(monitor: MonitorDetailsDto): Icon {
    return when {
        !monitor.enabled -> Icon.HEART_OFF
        monitor.uptimeStatus == UptimeStatus.UP -> Icon.HEART
        monitor.uptimeStatus == UptimeStatus.DOWN -> Icon.HEART_BROKEN
        else -> Icon.HEART
    }
}
