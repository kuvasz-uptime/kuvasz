package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderStatusPageList(statusPages: List<StatusPageDto>, appGlobals: AppGlobals): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = appGlobals.editabilityState.areStatusPagesReadOnly()
        div {
            classes(CARD_TABLE, TABLE_RESPONSIVE)
            table {
                classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                thead {
                    tr {
                        // ID
                        th {
                            classes(TEXT_CENTER)
                            +"ID"
                        }
                        // Title
                        th { +Messages.title() }
                        // Slug
                        th {
                            classes(D_NONE, D_MD_TABLE_CELL)
                            +Messages.slug()
                        }
                        // Preview
                        th {}
                        // Visibility
                        th {
                            classes(TEXT_CENTER)
                            +Messages.public()
                        }
                        // Monitors count
                        th {
                            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                            +Messages.monitors()
                        }
                        // Actions
                        if (!isReadOnlyMode) {
                            th {}
                        }
                    }
                }
                tbody {
                    // Default status page - special row
                    tr {
                        // ID
                        th {
                            classes(TEXT_CENTER)
                            +"0"
                        }
                        // Title
                        td {
                            classes(TEXT_WRAP, TEXT_BREAK)
                            span {
                                classes(ME_2)
                                val title = appGlobals.defaultStatusPageSettings.title
                                if (title.length > STATUS_PAGE_TITLE_MAX_LENGTH) {
                                    tooltip(title)
                                }
                                +title.abbreviate(STATUS_PAGE_TITLE_MAX_LENGTH)
                            }
                            inlineBadge(
                                text = Messages.default(),
                                tooltip = Messages.defaultStatusPageDescription(),
                            )
                        }
                        // Slug
                        td {
                            classes(D_NONE, D_MD_TABLE_CELL)
                            code {
                                +UIDefaults.STATUS_PAGE_PATH
                            }
                        }
                        // Preview
                        td {
                            classes(TEXT_CENTER)
                            // Preview button
                            statusPagePreviewButton(slug = "")
                        }
                        // Visibility
                        td {
                            classes(TEXT_CENTER)
                            statusPageVisibilityStatus(appGlobals.defaultStatusPageSettings.public)
                        }
                        // Monitors count
                        td {
                            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                        }
                        // Actions
                        if (!isReadOnlyMode) {
                            td {}
                        }
                    }
                    // Status pages
                    statusPages.forEach { page -> statusPageListItem(isReadOnlyMode, page) }
                }
            }
        }
    }

private fun FlowContent.statusPageVisibilityStatus(isPublic: Boolean) {
    val icon = if (isPublic) {
        Icon.CIRCLE_CHECK_FILLED
    } else {
        Icon.CIRCLE_X_FILLED
    }
    val colorClass: CSSClass? = if (isPublic) {
        TEXT_GREEN
    } else {
        TEXT_SECONDARY
    }

    span {
        colorClass?.let { classes(it) }
        icon(icon)
    }
}

private fun TBODY.statusPageListItem(isReadOnlyMode: Boolean, page: StatusPageDto) {
    tr {
        xData("statusPageListItem(${page.id}, ${page.public})")
        // ID
        th {
            classes(TEXT_CENTER)
            +page.id.toString()
        }
        // Title
        td {
            a(href = "/status-pages/${page.id}") {
                classes(TEXT_RESET)
                span {
                    classes(TEXT_WRAP, TEXT_BREAK)
                    +page.title.abbreviate(STATUS_PAGE_TITLE_MAX_LENGTH)
                }
            }
        }
        // Slug
        td {
            classes(D_NONE, D_MD_TABLE_CELL)
            code {
                +"/status/${page.slug.urlEncode()}"
            }
        }
        // Preview
        td {
            classes(TEXT_CENTER)
            // Preview button
            statusPagePreviewButton(slug = page.slug)
        }
        // Visibility
        td {
            classes(TEXT_CENTER)
            statusPageVisibilityStatus(page.public)
        }
        // Monitors count
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
            +page.monitors.size.toString()
        }
        // Actions
        if (!isReadOnlyMode) {
            td {
                classes(TEXT_CENTER)
                val deleteModalId = "delete-status-page-modal-${page.id}"
                div {
                    classes(FLEX_NOWRAP, BTN_GROUP)
                    // Publish / Unpublish button
                    toggleVisibilityButton(
                        isPublic = page.public,
                        xDisabledIf = "isRequestLoading",
                        xOnClick = "toggleStatusPageVisibility()",
                    )
                    // Delete button
                    deleteButton(
                        deleteModalId = deleteModalId,
                        xDisabledIf = "isRequestLoading",
                    )
                }
                deleteStatusPageModal(
                    modalId = deleteModalId,
                    statusPageTitle = page.title
                )
            }
        }
    }
}

fun FlowContent.statusPagePreviewButton(slug: String) {
    a(href = "/status/${slug.urlEncode()}") {
        targetBlank()
        classes(setOf(BTN, BTN_ICON))
        icon(Icon.EXTERNAL_LINK)
    }
}

private fun FlowContent.toggleVisibilityButton(
    isPublic: Boolean,
    xDisabledIf: String,
    xOnClick: String,
) {
    val toggleIcon = if (isPublic) Icon.SCREEN_SHARE_OFF else Icon.SCREEN_SHARE
    val toggleTooltip = if (isPublic) Messages.unpublish() else Messages.publish()

    compactIconButton(toggleIcon) {
        xBindDisabled(xDisabledIf)
        xOnClick(xOnClick)
        tooltip(toggleTooltip)
    }
}

private fun FlowContent.deleteButton(deleteModalId: String, xDisabledIf: String) {
    compactIconButton(Icon.TRASH, classes = setOf(TEXT_RED)) {
        xBindDisabled(xDisabledIf)
        modalOpener(deleteModalId)
    }
}
