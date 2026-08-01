package com.kuvaszuptime.kuvasz.ui.pages.monitor.dns

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

fun renderDnsMonitorsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.dnsMonitors(),
        pageTitle = { dnsMonitorsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/dns-monitors/fragments/list")
                            trigger {
                                load()
                                event("refresh-monitor-list")
                                every(15.seconds)
                            }
                            onSwapReinitTooltips()
                        }
                        id = "dns-monitors-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.dnsMonitorsHeader(globals: AppGlobals) {
    val createDnsModalId = "create-dns-monitor-modal"
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
                            +"DNS"
                            if (globals.editabilityState.areDnsMonitorsReadOnly()) {
                                readOnlyBadge(Messages.readOnlyDnsMonitors())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.areDnsMonitorsReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createDnsModalId)
                                    testId("add-new-button")
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createDnsModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshDnsMonitorList()") {}
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.areDnsMonitorsReadOnly()) {
            dnsMonitorCreateUpdateModal(modalId = createDnsModalId, monitor = null, globals)
        }
    }
}
