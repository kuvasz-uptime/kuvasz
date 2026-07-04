package com.kuvaszuptime.kuvasz.ui.pages.maintenance

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.maintenance.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderMaintenanceWindowsPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.maintenanceWindows(),
        pageTitle = { maintenanceWindowsHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/maintenance-windows/fragments/list")
                            trigger {
                                load()
                                event("refresh-maintenance-window-list")
                            }
                            onSwapReinitTooltips()
                        }
                        id = "maintenance-window-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.maintenanceWindowsHeader(globals: AppGlobals) {
    val createModalId = "create-maintenance-window-modal"
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
                            +Messages.maintenanceWindows()
                            // Read only notice
                            if (globals.editabilityState.areMaintenanceWindowsReadOnly()) {
                                readOnlyBadge(Messages.readOnlyMaintenanceWindows())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.areMaintenanceWindowsReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewMaintenanceWindow(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createModalId)
                                    testId("add-new-button")
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createModalId)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.areMaintenanceWindowsReadOnly()) {
            maintenanceWindowCreateUpdateModal(modalId = createModalId, maintenanceWindow = null, globals)
        }
    }
}
