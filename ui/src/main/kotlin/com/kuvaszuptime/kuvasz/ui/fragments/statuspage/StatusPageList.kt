package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

internal const val CREATE_STATUS_PAGE_MODAL_ID = "create-status-page-modal"
internal const val STATUS_PAGE_LIST_ID = "status-page-list"
private const val STATUS_PAGE_LIST_PATH = "/status-pages"
private const val STATUS_PAGE_SLUG_MAX_LENGTH = 50
private const val CLONED_SLUG_SUFFIX = "-copy"

/** The filters of the status page list, named after the query parameters of the v3 API. */
class StatusPageListFilters internal constructor(params: QueryParams) : ListFilters {

    val name: TextFilter = textFilter(params, "name", Messages.title(), placeholder = Messages.searchByTitle())

    val public: ChoiceFilter<Boolean> = yesNoFilter(params, "public", Messages.public())

    override val all: List<ListFilter> = listOf(name, public)
}

typealias StatusPageListQuery = ListQuery<StatusPageSortField, StatusPageListFilters>

/** The state of the status page list, parsed from the query parameters of its page or fragment. */
fun statusPageListQuery(params: QueryParams): StatusPageListQuery = ListQuery.parse(
    params = params,
    path = STATUS_PAGE_LIST_PATH,
    filters = StatusPageListFilters(params),
    sortFields = StatusPageSortField.entries,
    defaultSorting = ListDefaults.STATUS_PAGE_SORTING,
)

/**
 * Whether the default status page is listed. It's configured in the settings instead of being stored with the other
 * ones, so it's not in their pages: it's pinned to the top of the first page instead, as long as it matches the filters.
 */
internal fun StatusPageListQuery.listsDefaultStatusPage(settings: AppGlobals.DefaultStatusPageSettings): Boolean =
    pageRequest.page == PageRequest.DEFAULT_PAGE &&
        filters.name.value?.let { settings.title.contains(it, ignoreCase = true) } != false &&
        filters.public.value?.let { it == settings.public } != false

fun renderStatusPageList(
    statusPages: PagedResult<StatusPageDto>,
    query: StatusPageListQuery,
    appGlobals: AppGlobals,
): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false).run {
        val isReadOnlyMode = appGlobals.editabilityState.areStatusPagesReadOnly()
        val listsDefaultStatusPage = query.listsDefaultStatusPage(appGlobals.defaultStatusPageSettings)
        div {
            // The page shown, which is the last one if the requested page is past the end, see listQuery in kuvasz.js
            attributes["data-list-page"] = "${statusPages.page}"
            activeFilterCount(STATUS_PAGE_LIST_ID, query.activeFilterCount, outOfBand = true)
            if (statusPages.result.isEmpty() && !listsDefaultStatusPage) {
                emptyState(
                    icon = Icon.FILTER,
                    title = Messages.noMatchingStatusPages(),
                    subtitle = Messages.noMatchingStatusPagesDescription(),
                )
            } else {
                div {
                    classes(TABLE_RESPONSIVE)
                    table {
                        classes(TABLE, TABLE_SM, TABLE_VCENTER, CARD_TABLE)
                        thead {
                            tr {
                                sortableHeader(query, StatusPageSortField.ID, "ID", setOf(TEXT_CENTER))
                                sortableHeader(query, StatusPageSortField.TITLE, Messages.title())
                                sortableHeader(
                                    query,
                                    StatusPageSortField.SLUG,
                                    Messages.slug(),
                                    setOf(D_NONE, D_MD_TABLE_CELL),
                                )
                                // Preview
                                th {}
                                sortableHeader(query, StatusPageSortField.PUBLIC, Messages.public(), setOf(TEXT_CENTER))
                                // Monitors count
                                th {
                                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                    +Messages.monitors()
                                }
                                // Categories count
                                th {
                                    classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
                                    +Messages.categories()
                                }
                                // Actions
                                th {}
                            }
                        }
                        tbody {
                            if (listsDefaultStatusPage) {
                                defaultStatusPageRow(appGlobals.defaultStatusPageSettings)
                            }
                            statusPages.result.forEach { page -> statusPageListItem(isReadOnlyMode, page) }
                        }
                    }
                }
                if (statusPages.result.isNotEmpty()) {
                    listPagination(statusPages, query)
                }
            }
        }
    }

private fun TBODY.defaultStatusPageRow(settings: AppGlobals.DefaultStatusPageSettings) {
    val isAvailable = settings.isAvailable()
    tr {
        testId("default-status-page-row")
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
                val title = settings.title
                if (title.length > STATUS_PAGE_TITLE_MAX_LENGTH) {
                    tooltip(title)
                }
                +title.abbreviate(STATUS_PAGE_TITLE_MAX_LENGTH)
            }
            inlineBadge(
                text = Messages.default(),
                tooltip = Messages.defaultStatusPageDescription(),
            )
            if (!isAvailable) {
                span {
                    classes(MS_1)
                    testId("default-status-page-unavailable-badge")
                    inlineBadge(
                        text = Messages.unavailable(),
                        color = Color.ORANGE_LT,
                        tooltip = Messages.defaultStatusPageUnavailableDescription(
                            StatusPageDefaults.DEFAULT_PAGE_MAX_MONITORS,
                        ),
                    )
                }
            }
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
            if (isAvailable) {
                statusPagePreviewButton(slug = "")
            } else {
                compactIconButton(Icon.EXTERNAL_LINK, classes = setOf(BTN_SM)) {
                    testId("default-status-page-preview-unavailable")
                    attributes["disabled"] = "disabled"
                }
            }
        }
        // Visibility
        td {
            classes(TEXT_CENTER)
            statusPageVisibilityStatus(settings.public)
        }
        // Monitors count: the default page always shows everything enabled, so it has no count
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
        }
        // Categories count: it has no selectors at all either
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
        }
        // Actions: it's configured in the settings instead
        td {}
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
        testId(if (isPublic) "status-page-public-indicator" else "status-page-private-indicator")
        icon(icon)
    }
}

private fun TBODY.statusPageListItem(isReadOnlyMode: Boolean, page: StatusPageDto) {
    tr {
        testId("status-page-row")
        val editTitle = if (isReadOnlyMode) {
            Messages.configurationOf(page.title)
        } else {
            Messages.updateStatusPage(page.title)
        }
        // A copy starts private, so it isn't published before it's adjusted
        val clonedFields = mapOf(
            "title" to Messages.clonedStatusPageTitle(page.title),
            "slug" to clonedSlugOf(page.slug),
            "public" to false,
        ).asJsonString()
        xData("statusPageListItem(${page.id}, ${page.public}, $clonedFields, ${editTitle.asJsonString()})")
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
        // Categories count
        td {
            classes(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER)
            testId("status-page-categories")
            +page.categories.size.toString()
        }
        // Actions
        td {
            classes(TEXT_END)
            val deleteModalId = "delete-status-page-modal-${page.id}"
            div {
                classes(FLEX_NOWRAP, BTN_GROUP, BTN_GROUP_SM)
                compactIconButton(Icon.SETTINGS) {
                    testId(if (isReadOnlyMode) "status-page-configuration-button" else "status-page-configure-button")
                    modalOpener(CREATE_STATUS_PAGE_MODAL_ID)
                    xOnClick("editStatusPage()")
                }
                if (!isReadOnlyMode) {
                    compactIconButton(Icon.COPY) {
                        testId("status-page-clone-button")
                        modalOpener(CREATE_STATUS_PAGE_MODAL_ID)
                        xOnClick("cloneStatusPage()")
                    }
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
            }
            if (!isReadOnlyMode) {
                deleteStatusPageModal(
                    modalId = deleteModalId,
                    statusPageTitle = page.title
                )
            }
        }
    }
}

// The slug has to be unique as well, so the clone gets a suffixed one, which still fits into the maximum length
private fun clonedSlugOf(slug: String): String =
    slug.take(STATUS_PAGE_SLUG_MAX_LENGTH - CLONED_SLUG_SUFFIX.length) + CLONED_SLUG_SUFFIX

fun FlowContent.statusPagePreviewButton(slug: String) {
    a(href = "/status/${slug.urlEncode()}") {
        targetBlank()
        classes(setOf(BTN, BTN_SM, BTN_ICON))
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
        testId("status-page-toggle-visibility-button")
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
