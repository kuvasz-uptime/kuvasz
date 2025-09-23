package com.kuvaszuptime.kuvasz.ui.pages.monitor.http

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderHttpMonitorsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.monitors(),
        pageTitle = { httpMonitorsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/http-monitors/fragments/list")
                            trigger {
                                load()
                                event("refresh-monitor-list")
                            }
                            onSwapReinitTooltips()
                        }
                        id = "monitors-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.httpMonitorsHeader(globals: AppGlobals) {
    val createHttpModalId = "create-http-monitor-modal"
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
                            +Messages.monitors()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +"HTTP & SSL"
                            // Read only notice
                            if (globals.editabilityState.areHttpMonitorsReadOnly()) {
                                readOnlyBadge(Messages.readOnlyHttpMonitors())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createHttpModalId)
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createHttpModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshHttpMonitorList()") {}
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
            httpMonitorCreateUpdateModal(modalId = createHttpModalId, monitor = null, globals)
        }
    }
}
