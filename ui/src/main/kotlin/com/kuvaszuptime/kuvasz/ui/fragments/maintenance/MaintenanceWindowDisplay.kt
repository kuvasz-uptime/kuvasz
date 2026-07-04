package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowType
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal const val MAINTENANCE_WINDOW_NAME_MAX_LENGTH = 40

internal fun MaintenanceWindowDetailsDto.statusText(): String = when {
    active -> Messages.maintenanceWindowActive()
    enabled -> Messages.enabled()
    else -> Messages.disabled()
}

@Suppress("MagicNumber")
internal fun FlowContent.maintenanceWindowStatusIndicator(window: MaintenanceWindowDetailsDto) {
    val animated = window.active
    span {
        val statusClasses =
            mutableSetOf(STATUS_INDICATOR, window.statusColor()).addIf(animated, STATUS_INDICATOR_ANIMATED)
        classes(statusClasses)
        tooltip(window.statusText())
        repeat(3) {
            span { classes(STATUS_INDICATOR_CIRCLE) }
        }
    }
}

internal fun MaintenanceWindowDetailsDto.statusColor(): CSSClass = when {
    active -> STATUS_GREEN
    enabled -> STATUS_YELLOW
    else -> STATUS_CYAN
}

internal fun MaintenanceWindowDetailsDto.badgeColor(): Color = when {
    active -> Color.GREEN_LT
    enabled -> Color.YELLOW_LT
    else -> Color.DEFAULT
}

internal fun MaintenanceWindowDetailsDto.resolveType(): MaintenanceWindowType = when {
    cron != null -> MaintenanceWindowType.CRON
    start != null -> MaintenanceWindowType.SINGLE
    else -> MaintenanceWindowType.MANUAL
}

internal fun MaintenanceWindowType.label(): String = when (this) {
    MaintenanceWindowType.MANUAL -> Messages.maintenanceWindowTypeManual()
    MaintenanceWindowType.CRON -> Messages.maintenanceWindowTypeCron()
    MaintenanceWindowType.SINGLE -> Messages.maintenanceWindowTypeSingle()
}

internal fun MaintenanceWindowType.badgeColor(): Color = when (this) {
    MaintenanceWindowType.MANUAL -> Color.DEFAULT
    MaintenanceWindowType.CRON -> Color.BLUE_LT
    MaintenanceWindowType.SINGLE -> Color.ORANGE_LT
}

internal fun MaintenanceWindowType.icon(): Icon = when (this) {
    MaintenanceWindowType.MANUAL -> Icon.TOGGLE_RIGHT
    MaintenanceWindowType.CRON -> Icon.REPEAT
    MaintenanceWindowType.SINGLE -> Icon.CALENDAR_EVENT
}
