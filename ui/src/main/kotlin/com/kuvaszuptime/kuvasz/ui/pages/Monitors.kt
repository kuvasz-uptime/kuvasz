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

fun renderMonitorsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.monitors(),
        pageTitle = { monitorsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/fragments/monitors/list")
                            trigger {
                                load()
                                event("refresh-monitor-list")
                            }
                            onSwapReinitTooltips()
                        }
                        id = "monitors-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            attributes["role"] = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.monitorsHeader(globals: AppGlobals) {
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
                            +Messages.overview()
                        }
                        h2 {
                            classes(PAGE_TITLE)
                            +Messages.monitors()
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.isReadOnlyMode()) {
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
                            compactIconButton(Icon.REFRESH, onClick = "refreshMonitorList()") {}
                            div {
                                classes(DROPDOWN)
                                a(href = "#") {
                                    classes(BTN, DROPDOWN_TOGGLE)
                                    dropdownToggler()
                                    icon(Icon.SETTINGS)
                                }
                                div {
                                    classes(DROPDOWN_MENU)
                                    a(href = "/api/v1/monitors/export/yaml") {
                                        classes(DROPDOWN_ITEM)
                                        attributes["download"] = "true"
                                        icon(Icon.DOWNLOAD)
                                        +Messages.downloadYamlBackup()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!globals.isReadOnlyMode()) {
            monitorCreateUpdateModal(modalId = createModalId, monitor = null, globals)
        }
    }
}
