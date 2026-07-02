package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMaintenanceWindowDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun FlowContent.maintenanceBanner(pageData: StatusPageDataDto) {
    val active = pageData.activeMaintenanceWindows
    if (active.isEmpty()) return

    div {
        classes(CONTAINER_XL, MB_6)
        testId("status-page-maintenance-banner")
        maintenanceCard(
            title = Messages.statusPageActiveMaintenanceTitle(),
            windows = active,
            testId = "status-page-active-maintenance",
        )
    }
}

private fun FlowContent.maintenanceCard(
    title: String,
    windows: List<StatusPageMaintenanceWindowDto>,
    testId: String,
) {
    div {
        classes(CARD)
        testId(testId)
        // Yellow side strip
        div {
            classes(CARD_STATUS_START, BG_YELLOW)
        }
        // Icon stamp in the top-right corner
        div {
            classes(CARD_STAMP)
            div {
                classes(CARD_STAMP_ICON, BG_YELLOW)
                icon(Icon.TOOL)
            }
        }
        div {
            classes(CARD_BODY)
            h3 {
                classes(CARD_TITLE)
                +title
            }
            ul {
                classes(LIST_UNSTYLED, MB_0)
                windows.forEachIndexed { idx, window ->
                    if (idx > 0) hr()
                    li {
                        classes(MB_2)
                        div {
                            span {
                                classes(ME_2)
                                icon(Icon.TOOL)
                            }
                            strong { +window.name }
                        }
                        window.description?.takeIf { it.isNotBlank() }?.let { description ->
                            div {
                                classes(TEXT_MUTED, MT_2)
                                +description
                            }
                        }
                        maintenanceTimeframe(window)
                    }
                }
            }
        }
    }
}

private fun FlowContent.maintenanceTimeframe(window: StatusPageMaintenanceWindowDto) {
    val start = window.start
    val end = window.end
    if (start == null || end == null) return

    div {
        classes(D_INLINE_FLEX, ALIGN_ITEMS_CENTER, MT_2, MB_2)
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
