package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * The page numbers in the pagination of a list on [page] of [totalPages]: the first and the last ones, and the
 * neighbours of the current one. A null stands for the pages left out between them, unless that would be a single
 * page, which is shown instead.
 */
internal fun paginationWindow(page: Int, totalPages: Int): List<Int?> {
    val shown = (listOf(1, totalPages) + (page - 1..page + 1)).filter { it in 1..totalPages }.distinct().sorted()
    return shown.flatMapIndexed { index, number ->
        val gap = number - (shown.getOrNull(index - 1) ?: (number - 1))
        when (gap) {
            1 -> listOf(number)
            2 -> listOf(number - 1, number)
            else -> listOf(null, number)
        }
    }
}

/**
 * The footer of a paged list with the range of the items shown out of all of them, the page size and the pages, linked
 * to the URLs of the list with the same state, so they work as permalinks. A plain click is handled by the listQuery
 * Alpine.js component instead, see kuvasz.js.
 */
internal fun <T> FlowContent.listPagination(page: PagedResult<T>, query: ListQuery<*, *>) {
    div {
        // Centered on the small screens, where the parts of the footer wrap under each other
        classes(CARD_FOOTER, D_FLEX, FLEX_WRAP, ALIGN_ITEMS_CENTER, JUSTIFY_CONTENT_CENTER, JUSTIFY_CONTENT_MD_START, GAP_3)
        testId("pagination")
        val offset = (page.page - 1).toLong() * page.size
        p {
            classes(M_0, TEXT_SECONDARY)
            testId("pagination-summary")
            +Messages.listPaginationSummary(offset + 1, offset + page.result.size, page.totalCount)
        }
        pageSizeSelect(page.size)
        ul {
            classes(PAGINATION, M_0, MS_MD_AUTO)
            pageItem(
                targetPage = (page.page - 1).takeIf { it >= 1 },
                query = query,
                ariaLabel = Messages.previousPage(),
            ) { icon(Icon.CHEVRON_LEFT) }
            paginationWindow(page.page, page.totalPages).forEach { number ->
                if (number == null) {
                    li {
                        classes(PAGE_ITEM, DISABLED)
                        span {
                            classes(PAGE_LINK)
                            +"…"
                        }
                    }
                } else {
                    pageItem(targetPage = number, query = query, isCurrent = number == page.page) { +"$number" }
                }
            }
            pageItem(
                targetPage = (page.page + 1).takeIf { it <= page.totalPages },
                query = query,
                ariaLabel = Messages.nextPage(),
            ) { icon(Icon.CHEVRON_RIGHT) }
        }
    }
}

private fun UL.pageItem(
    targetPage: Int?,
    query: ListQuery<*, *>,
    isCurrent: Boolean = false,
    ariaLabel: String? = null,
    content: HTMLTag.() -> Unit,
) {
    li {
        classes(
            buildSet {
                add(PAGE_ITEM)
                if (isCurrent) add(ACTIVE)
                if (targetPage == null) add(DISABLED)
            }
        )
        if (targetPage == null) {
            span {
                classes(PAGE_LINK)
                ariaLabel?.let { ariaLabel(it) }
                content()
            }
        } else {
            a(href = query.pageUrl(targetPage)) {
                classes(PAGE_LINK)
                testId("page-link")
                ariaLabel?.let { ariaLabel(it) }
                if (isCurrent) attributes["aria-current"] = "page"
                xOnClick("followLink(\$event)")
                content()
            }
        }
    }
}

private fun FlowContent.pageSizeSelect(size: Int) {
    label {
        classes(D_FLEX, ALIGN_ITEMS_CENTER, GAP_2, M_0, TEXT_SECONDARY, TEXT_NOWRAP)
        +Messages.rowsPerPage()
        select {
            classes(FORM_SELECT, FORM_SELECT_SM, W_AUTO)
            testId("page-size-select")
            xOnChange("setSize(\$event.target.value)")
            ListQuery.PAGE_SIZES.forEach { pageSize ->
                option {
                    value = "$pageSize"
                    selected = pageSize == size
                    +"$pageSize"
                }
            }
        }
    }
}
