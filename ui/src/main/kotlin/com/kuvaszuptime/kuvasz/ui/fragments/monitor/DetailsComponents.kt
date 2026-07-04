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
        monitor.uptimeStatus.cardStatusClass(monitor.inMaintenance)
    }

internal fun UptimeStatus?.cardStatusClass(inMaintenance: Boolean): CSSClass {
    if (inMaintenance) return BG_SECONDARY
    return when (this) {
        UptimeStatus.UP -> BG_SUCCESS
        UptimeStatus.DOWN -> BG_DANGER
        null -> BG_WARNING
    }
}

internal fun getUptimeCardIcon(monitor: MonitorDetailsDto): Icon =
    when {
        !monitor.enabled -> Icon.HEART_OFF
        monitor.inMaintenance -> Icon.TOOL
        monitor.uptimeStatus == UptimeStatus.UP -> Icon.HEART
        monitor.uptimeStatus == UptimeStatus.DOWN -> Icon.HEART_BROKEN
        else -> Icon.HEART
    }
