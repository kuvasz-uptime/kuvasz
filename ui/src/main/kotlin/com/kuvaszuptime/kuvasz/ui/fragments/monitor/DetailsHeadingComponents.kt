package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

internal const val MONITOR_TARGET_MAX_LENGTH = 40

/**
 * The heading of a monitor's details page: its status, id, name and a list of badges. The badge naming the monitor's
 * type is always there, [extraBadges] is where a type adds the ones describing what it checks.
 */
internal fun FlowContent.monitorDetailsHeading(
    typeUiConfig: MonitorTypeUiConfig,
    monitor: MonitorDetailsDto,
    extraBadges: UL.() -> Unit = {},
) {
    div {
        id = "${typeUiConfig.slug}-monitor-detail-heading"
        classes(COL_AUTO)
        hx {
            get(typeUiConfig.fragmentPath("details-heading/${monitor.id}"))
            trigger {
                every(15.seconds)
                event("refresh-monitor-detail-status")
            }
            onSwapReinitTooltips()
        }

        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            div {
                classes(COL_AUTO)
                uptimeStatusOfMonitor(monitor, withTooltip = false)
            }
            div {
                classes(CSSClass.COL)
                div {
                    classes(PAGE_PRETITLE)
                    +"#${monitor.id}"
                }
                h2 {
                    classes(PAGE_TITLE, TEXT_WRAP, TEXT_BREAK)
                    +monitor.name.abbreviate(MONITOR_NAME_MAX_LENGTH)
                }
                div {
                    classes(TEXT_SECONDARY)
                    ul {
                        classes(LIST_INLINE, MT_1, MB_0)
                        a(href = typeUiConfig.listPath) {
                            classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                            inlineStatusBadge(
                                text = typeUiConfig.title,
                                icon = typeUiConfig.icon,
                                color = typeUiConfig.color
                            )
                        }
                        monitor.category?.let { category ->
                            li {
                                classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
                                testId("monitor-category-badge")
                                inlineStatusBadge(
                                    text = category.abbreviate(MONITOR_TARGET_MAX_LENGTH),
                                    icon = Icon.TAG,
                                    color = Color.DEFAULT,
                                )
                            }
                        }
                        extraBadges()
                    }
                }
            }
        }
    }
}

/** A badge of [monitorDetailsHeading] describing what the monitor targets, e.g. its host. */
internal fun UL.monitorTargetBadge(text: String, icon: Icon) {
    li {
        classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
        inlineStatusBadge(text = text, icon = icon, color = Color.DEFAULT)
    }
}
