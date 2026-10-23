package com.kuvaszuptime.kuvasz.ui.pages.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderStatusPagesPage(globals: AppGlobals, query: StatusPageListQuery) =
    withLayout(
        globals,
        title = Messages.statusPages(),
        pageTitle = { statusPagesHeader(globals) }
    ) {
        div {
            classes(ROW, ROW_CARDS)
            div {
                classes(COL_12)
                pagedListCard(
                    query = query,
                    listElementId = STATUS_PAGE_LIST_ID,
                    fragmentPath = "/status-pages/fragments/list",
                    refreshEvent = "refresh-status-page-list",
                    refreshInterval = null,
                )
            }
        }
    }

internal fun HtmlBlockTag.statusPagesHeader(globals: AppGlobals) {
    val createStatusPageModalId = CREATE_STATUS_PAGE_MODAL_ID
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
        statusPageCreateUpdateModal(modalId = createStatusPageModalId, statusPage = null, globals)
    }
}
