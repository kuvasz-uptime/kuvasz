package com.kuvaszuptime.kuvasz.ui.pages

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
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
                get("/http-monitors/fragments/stats")
                trigger {
                    load()
                    every(30.seconds)
                    event("refresh-dashboard")
                }
                onSwapReinitTooltips()
            }
            id = "http-monitoring-dashboard"
            div {
                classes(SPINNER_GROW, HTMX_INDICATOR)
                role = "status"
            }
        }
        div {
            hx {
                get("/push-monitors/fragments/stats")
                trigger {
                    load()
                    every(30.seconds)
                    event("refresh-dashboard")
                }
                onSwapReinitTooltips()
            }
            id = "push-monitoring-dashboard"
            div {
                classes(SPINNER_GROW, HTMX_INDICATOR)
                role = "status"
            }
        }
        div {
            hx {
                get("/icmp-monitors/fragments/stats")
                trigger {
                    load()
                    every(30.seconds)
                    event("refresh-dashboard")
                }
                onSwapReinitTooltips()
            }
            id = "icmp-monitoring-dashboard"
            div {
                classes(SPINNER_GROW, HTMX_INDICATOR)
                role = "status"
            }
        }
    }

private fun HtmlBlockTag.dashboardHeader(globals: AppGlobals) {
    val createHttpModalId = "create-http-monitor-modal"
    val createPushModalId = "create-push-monitor-modal"
    val createIcmpModalId = "create-icmp-monitor-modal"
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
                            +Messages.monitoring()
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            div {
                                classes(DROPDOWN)
                                a(href = "#") {
                                    classes(BTN, DROPDOWN_TOGGLE, BTN_PRIMARY)
                                    dropdownToggler()
                                    icon(Icon.PLUS)
                                    span {
                                        classes(D_NONE, D_MD_BLOCK)
                                        +Messages.addNewMonitor()
                                    }
                                }
                                div {
                                    classes(DROPDOWN_MENU)
                                    button {
                                        val isReadOnly = globals.editabilityState.areHttpMonitorsReadOnly()
                                        classes(DROPDOWN_ITEM)
                                        modalOpener(createHttpModalId)
                                        disabled = isReadOnly
                                        +Messages.httpSslMonitor()
                                        if (isReadOnly) {
                                            readOnlyBadge(Messages.readOnlyHttpMonitors())
                                        }
                                    }
                                    button {
                                        val isReadOnly = globals.editabilityState.arePushMonitorsReadOnly()
                                        classes(DROPDOWN_ITEM)
                                        modalOpener(createPushModalId)
                                        disabled = isReadOnly
                                        +Messages.pushMonitor()
                                        if (isReadOnly) {
                                            readOnlyBadge(Messages.readOnlyPushMonitors())
                                        }
                                    }
                                    button {
                                        val isReadOnly = globals.editabilityState.areIcmpMonitorsReadOnly()
                                        classes(DROPDOWN_ITEM)
                                        modalOpener(createIcmpModalId)
                                        disabled = isReadOnly
                                        +Messages.icmpMonitor()
                                        if (isReadOnly) {
                                            readOnlyBadge(Messages.readOnlyIcmpMonitors())
                                        }
                                    }
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshDashboard()") {}
                        }
                    }
                }
            }
        }
    }
    // Render the upsert modals conditionally
    if (!globals.editabilityState.areHttpMonitorsReadOnly()) {
        httpMonitorCreateUpdateModal(modalId = createHttpModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.arePushMonitorsReadOnly()) {
        pushMonitorCreateUpdateModal(modalId = createPushModalId, monitor = null, globals)
    }
    if (!globals.editabilityState.areIcmpMonitorsReadOnly()) {
        icmpMonitorCreateUpdateModal(modalId = createIcmpModalId, monitor = null, globals)
    }
}
