package com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlin.time.Duration.Companion.seconds

fun renderIcmpMonitorsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.icmpMonitors(),
        pageTitle = { icmpMonitorsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/icmp-monitors/fragments/list")
                            trigger {
                                load()
                                event("refresh-monitor-list")
                                every(15.seconds)
                            }
                            onSwapReinitTooltips()
                        }
                        id = "icmp-monitors-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.icmpMonitorsHeader(globals: AppGlobals) {
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
                            +Messages.monitors()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +"ICMP"
                            if (globals.editabilityState.areIcmpMonitorsReadOnly()) {
                                readOnlyBadge(Messages.readOnlyIcmpMonitors())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.areIcmpMonitorsReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMonitor(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createIcmpModalId)
                                    testId("add-new-button")
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createIcmpModalId)
                                }
                            }
                            compactIconButton(Icon.REFRESH, onClick = "refreshIcmpMonitorList()") {}
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.areIcmpMonitorsReadOnly()) {
            icmpMonitorCreateUpdateModal(modalId = createIcmpModalId, monitor = null, globals)
        }
    }
}
