package com.kuvaszuptime.kuvasz.ui.pages.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderStatusPageDetailsPage(
    globals: AppGlobals,
    statusPage: StatusPageDto,
    pageData: StatusPageDataDto,
): String =
    withLayout(
        globals,
        title = pageData.title.abbreviate(STATUS_PAGE_TITLE_MAX_LENGTH),
        pageTitle = { statusPageDetailsHeader(statusPage, globals) },
    ) {
        statusPageDetailsContent(pageData)
    }

internal fun HtmlBlockTag.statusPageDetailsHeader(
    statusPage: StatusPageDto,
    globals: AppGlobals,
) {
    val deleteModalId = "delete-status-page-modal-${statusPage.id}"
    val updateModalId = "update-status-page-modal-${statusPage.id}"

    div {
        classes(CONTAINER)
        xData("statusPageDetails(${statusPage.id}, ${statusPage.public})")
        div {
            classes(ROW, G_3, ALIGN_ITEMS_CENTER)
            statusPageDetailsHeading(statusPage)
            div {
                classes(COL_MD_AUTO, MS_AUTO)
                div {
                    classes(BTN_LIST)
                    // Publish / Unpublish
                    if (!globals.editabilityState.areStatusPagesReadOnly()) {
                        button {
                            classes(BTN, BTN_ICON)
                            xBindDisabled("isRequestLoading")
                            xOnClick("toggleStatusPageVisibility()")
                            template {
                                attributes["x-if"] = "isStatusPagePublic"
                                icon(Icon.SCREEN_SHARE_OFF)
                            }
                            template {
                                attributes["x-if"] = "!isStatusPagePublic"
                                icon(Icon.SCREEN_SHARE)
                            }
                        }
                    }
                    // Configure + Delete
                    if (!globals.editabilityState.areStatusPagesReadOnly()) {
                        buttonWithIcon(Icon.SETTINGS, Messages.configure()) {
                            modalOpener(updateModalId)
                        }
                        compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
                            xBindDisabled("isRequestLoading")
                            modalOpener(deleteModalId)
                        }
                        deleteStatusPageModal(modalId = deleteModalId, statusPageTitle = statusPage.title)
                    } else {
                        // View
                        buttonWithIcon(Icon.EYE, Messages.configuration()) {
                            modalOpener(updateModalId)
                        }
                    }
                    statusPageCreateUpdateModal(updateModalId, statusPage, globals)
                }
            }
        }
    }
}
