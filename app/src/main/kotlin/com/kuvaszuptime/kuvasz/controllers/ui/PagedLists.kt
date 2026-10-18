package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.ui.components.*
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.views.htmx.http.HtmxResponseHeaders

internal fun HttpRequest<*>.queryParams(): QueryParams = { name -> parameters.getAll(name) }

/**
 * The page of a list the UI shows: the requested one, or the last one if the requested page is past the end, e.g.
 * because the last items were deleted since the page was opened. An empty list is shown on its first page.
 */
internal fun <T> fetchListPage(pageRequest: PageRequest, fetch: (PageRequest) -> PagedResult<T>): PagedResult<T> {
    val page = fetch(pageRequest)
    return when {
        page.page <= maxOf(page.totalPages, PageRequest.DEFAULT_PAGE) -> page
        page.totalPages == 0 -> page.copy(page = PageRequest.DEFAULT_PAGE)
        else -> fetch(pageRequest.copy(page = page.totalPages))
    }
}

/**
 * The fragment of a paged list, rendered by [render] with the state of the page shown. It also replaces the URL of
 * the list page with the one of that state, so the address bar is always a permalink of what is shown.
 */
internal fun <F : Enum<F>, L : ListFilters, T> pagedListFragment(
    query: ListQuery<F, L>,
    page: PagedResult<T>,
    render: (ListQuery<F, L>) -> String,
): HttpResponse<String> {
    val shownQuery = query.withPage(page.page)
    return HttpResponse.ok(render(shownQuery)).header(HtmxResponseHeaders.HX_REPLACE_URL, shownQuery.url)
}
