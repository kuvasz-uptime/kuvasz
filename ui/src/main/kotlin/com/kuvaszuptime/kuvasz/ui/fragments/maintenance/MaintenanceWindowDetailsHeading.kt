package com.kuvaszuptime.kuvasz.ui.fragments.maintenance

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

fun renderMaintenanceWindowDetailsHeading(maintenanceWindow: MaintenanceWindowDetailsDto): String =
    buildString { appendHTML().div { maintenanceWindowDetailsHeading(maintenanceWindow) } }

internal fun FlowContent.maintenanceWindowDetailsHeading(maintenanceWindow: MaintenanceWindowDetailsDto) {
    val type = maintenanceWindow.resolveType()
    div {
        id = "maintenance-window-detail-heading"
        classes(COL_AUTO)
        hx {
            get("/maintenance-windows/fragments/details-heading/${maintenanceWindow.id}")
            trigger {
                every(15.seconds)
                event("refresh-maintenance-window-detail-status")
            }
            onSwapReinitTooltips()
        }

        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_AUTO)
                maintenanceWindowStatusIndicator(maintenanceWindow)
            }
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +"#${maintenanceWindow.id}"
                }
                h2 {
                    classes(PAGE_TITLE, TEXT_WRAP, TEXT_BREAK)
                    +maintenanceWindow.name.abbreviate(MAINTENANCE_WINDOW_NAME_MAX_LENGTH)
                }
                div {
                    classes(TEXT_SECONDARY)
                    ul {
                        classes(LIST_INLINE, MT_1, MB_0)
                        a(href = "/maintenance-windows") {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = type.label(),
                                icon = Icon.TOOL,
                                color = type.badgeColor(),
                            )
                        }
                        li {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            val shownOnStatusPages = maintenanceWindow.showOnStatusPages
                            inlineStatusBadge(
                                text = if (shownOnStatusPages) {
                                    Messages.maintenanceWindowShownOnStatusPages()
                                } else {
                                    Messages.maintenanceWindowHiddenFromStatusPages()
                                },
                                color = if (shownOnStatusPages) Color.GREEN_LT else Color.DEFAULT,
                                icon = Icon.HEART_RATE_MONITOR
                            )
                        }
                    }
                }
            }
        }
    }
}
