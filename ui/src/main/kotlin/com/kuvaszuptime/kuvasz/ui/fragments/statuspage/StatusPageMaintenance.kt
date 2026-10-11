package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMaintenanceWindowDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.maintenanceWindowCard(window: StatusPageMaintenanceWindowDto) {
    statusCard(StatusPageAccordion.ACTIVE_MAINTENANCE.statusColor, cardTestId = "status-page-maintenance-window") {
        h4 {
            classes(CARD_TITLE, MB_0)
            +window.name
        }
        window.description?.takeIf { it.isNotBlank() }?.let { description ->
            div {
                classes(TEXT_SECONDARY, MT_2)
                +description
            }
        }
        maintenanceTimeframe(window)
    }
}

private fun FlowContent.maintenanceTimeframe(window: StatusPageMaintenanceWindowDto) {
    val start = window.start
    val end = window.end
    if (start == null || end == null) return

    div {
        classes(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, MT_2)
        testId("maintenance-window-timeframe")
        span {
            classes(ME_2, D_INLINE_FLEX)
            icon(Icon.CLOCK)
        }
        span { +start.toDateTimeStringWithZone() }
        span {
            classes(MS_2, ME_2, D_INLINE_FLEX)
            icon(Icon.ARROW_NARROW_RIGHT)
        }
        span { +end.toDateTimeStringWithZone() }
    }
}
