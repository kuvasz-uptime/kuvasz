package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*

internal fun SystemStatus.icon(): Icon = when (this) {
    SystemStatus.OPERATIONAL -> Icon.CIRCLE_CHECK_FILLED_LG
    SystemStatus.PARTIAL_OUTAGE -> Icon.ALERT_TRIANGLE_FILLED_LG
    SystemStatus.MAJOR_OUTAGE -> Icon.CIRCLE_X_FILLED_LG
    SystemStatus.PENDING -> Icon.HOURGLASS_LG
}

internal fun SystemStatus.color(): CSSClass = when (this) {
    SystemStatus.OPERATIONAL -> TEXT_GREEN
    SystemStatus.PARTIAL_OUTAGE -> TEXT_YELLOW
    SystemStatus.MAJOR_OUTAGE -> TEXT_RED
    SystemStatus.PENDING -> TEXT_SECONDARY
}

internal fun SystemStatus.title(): String = when (this) {
    SystemStatus.OPERATIONAL -> Messages.statusPageSystemStatusOperational()
    SystemStatus.PARTIAL_OUTAGE -> Messages.statusPageSystemStatusPartialOutage()
    SystemStatus.MAJOR_OUTAGE -> Messages.statusPageSystemStatusMajorOutage()
    SystemStatus.PENDING -> Messages.statusPageSystemStatusPending()
}

internal fun SystemStatus.description(): String = when (this) {
    SystemStatus.OPERATIONAL -> Messages.statusPageSystemStatusOperationalDescription()
    SystemStatus.PARTIAL_OUTAGE -> Messages.statusPageSystemStatusPartialOutageDescription()
    SystemStatus.MAJOR_OUTAGE -> Messages.statusPageSystemStatusMajorOutageDescription()
    SystemStatus.PENDING -> Messages.statusPageSystemStatusPendingDescription()
}
