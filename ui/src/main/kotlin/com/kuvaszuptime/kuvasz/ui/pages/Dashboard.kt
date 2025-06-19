package com.kuvaszuptime.kuvasz.ui.pages

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

fun renderDashboard(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.dashboard(),
        pageTitle = { dashboardHeader(globals) }
    ) {
        div {
            hx {
                get("/fragments/monitors/stats")
                trigger {
                    load()
                    every(30.seconds)
                    event("refresh-dashboard")
                }
                onSwapReinitTooltips()
            }
            id = "monitoring-dashboard"
            div {
                classes(SPINNER_GROW, HTMX_INDICATOR)
                attributes["role"] = "status"
            }
        }
    }

private fun HtmlBlockTag.dashboardHeader(globals: AppGlobals) {
    val createModalId = "create-monitor-modal"
    div {
        classes(CONTAINER_XL)
        div {
            classes(ROW, G_2, ALIGN_ITEMS_CENTER)
            div {
                classes(CSSClass.COL)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER)
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(PAGE_PRETITLE)
                            +Messages.dashboard()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +Messages.uptimeTitle()
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.isReadOnlyMode) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createModalId)
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshDashboard()") {}
                        }
                    }
                }
            }
        }
    }
    if (!globals.isReadOnlyMode) {
        monitorCreateUpdateModal(modalId = createModalId, monitor = null, globals)
    }
}
