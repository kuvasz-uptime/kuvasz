package com.kuvaszuptime.kuvasz.ui.pages.monitor.push

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderPushMonitorsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.pushMonitors(),
        pageTitle = { pushMonitorsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/push-monitors/fragments/list")
                            trigger {
                                load()
                                event("refresh-monitor-list")
                            }
                            onSwapReinitTooltips()
                        }
                        id = "push-monitors-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.pushMonitorsHeader(globals: AppGlobals) {
    val createPushModalId = "create-push-monitor-modal"
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
                            +"Push"
                            // Read only notice
                            if (globals.editabilityState.arePushMonitorsReadOnly()) {
                                readOnlyBadge(Messages.readOnlyPushMonitors())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.arePushMonitorsReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createPushModalId)
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createPushModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshPushMonitorList()") {}
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.arePushMonitorsReadOnly()) {
            pushMonitorCreateUpdateModal(modalId = createPushModalId, monitor = null, globals)
        }
    }
}
