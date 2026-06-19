package com.kuvaszuptime.kuvasz.ui.pages.statuspage

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderStatusPagesPage(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.statusPages(),
        pageTitle = { statusPagesHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                div {
                    classes(CARD)
                    div {
                        hx {
                            get("/status-pages/fragments/list")
                            trigger {
                                load()
                                event("refresh-status-page-list")
                            }
                            onSwapReinitTooltips()
                        }
                        id = "status-page-list"
                        div {
                            classes(SPINNER_GROW, HTMX_INDICATOR)
                            role = "status"
                        }
                    }
                }
            }
        }
    }

internal fun HtmlBlockTag.statusPagesHeader(globals: AppGlobals) {
    val createStatusPageModalId = "create-status-page-modal"
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
                            +Messages.statusPages()
                            // Read only notice
                            if (globals.editabilityState.areStatusPagesReadOnly()) {
                                readOnlyBadge(Messages.readOnlyStatusPages())
                            }
                        }
                    }
                    div {
                        classes(COL_AUTO, MS_AUTO)
                        div {
                            classes(BTN_LIST)
                            if (!globals.editabilityState.areStatusPagesReadOnly()) {
                                buttonWithIcon(
                                    icon = Icon.PLUS,
                                    label = Messages.addNewStatusPage(),
                                    classes = setOf(BTN_PRIMARY, D_NONE, D_MD_BLOCK)
                                ) {
                                    modalOpener(createStatusPageModalId)
                                    testId("add-new-button")
                                }
                                compactIconButton(Icon.PLUS, classes = setOf(BTN_PRIMARY, D_MD_NONE)) {
                                    modalOpener(createStatusPageModalId)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!globals.editabilityState.areStatusPagesReadOnly()) {
            statusPageCreateUpdateModal(modalId = createStatusPageModalId, statusPage = null, globals)
        }
    }
}
