package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.SelectOption
import java.util.regex.Pattern

/**
 * A paged list swapped into `#[listElementId]` by htmx, with its filter accordion above it, its sortable headers and
 * its pagination. Its state lives in the URL of the page, see [expectQuery].
 */
class PagedListView(private val page: Page, private val listElementId: String) {

    private val filtersToggle: Locator get() = page.getByTestId("accordion-toggle-$listElementId-filters-panel")

    val activeFilterCount: Locator get() = page.getByTestId("active-filter-count")

    val clearFiltersButton: Locator get() = page.getByTestId("clear-filters-button")

    val pagination: Locator get() = page.getByTestId("pagination")

    val paginationSummary: Locator get() = page.getByTestId("pagination-summary")

    val pageSizeSelect: Locator get() = page.getByTestId("page-size-select")

    fun filter(param: String): Locator = page.getByTestId("list-filter-$param")

    fun openFilters() {
        filtersToggle.click()
        assertThat(filter("name")).isVisible()
    }

    fun searchByName(text: String) {
        filter("name").fill(text)
    }

    fun choose(param: String, label: String) {
        filter(param).selectOption(SelectOption().setLabel(label))
    }

    fun chosenLabel(param: String): String =
        filter(param).evaluate("select => select.selectedOptions[0].textContent") as String

    // The choices of a multi choice filter are pills, which are toggled one by one
    fun toggle(param: String, label: String) {
        filter(param).getByText(label, Locator.GetByTextOptions().setExact(true)).click()
    }

    fun sortHeader(field: String): Locator = page.getByTestId("sort-by-$field")

    /** The `aria-sort` of the column, null if the list is not sorted by it. */
    fun sortStateOf(field: String): String? = sortHeader(field).locator("xpath=..").getAttribute("aria-sort")

    fun expectSortState(field: String, state: String) {
        assertThat(sortHeader(field).locator("xpath=..")).hasAttribute("aria-sort", state)
    }

    fun pageLink(number: Int): Locator =
        pagination.locator("a.page-link").filter(Locator.FilterOptions().setHasText(Pattern.compile("^$number$")))

    fun previousPageLink(): Locator = pagination.locator(".page-item:first-child .page-link")

    fun nextPageLink(): Locator = pagination.locator(".page-item:last-child .page-link")

    fun setPageSize(size: Int) {
        pageSizeSelect.selectOption("$size")
    }

    /**
     * Waits for the URL of the page to become [path] with exactly [query], which the list sets after every request of
     * its own, see `HX-Replace-Url`.
     */
    fun expectQuery(path: String, query: String = "") {
        val expected = if (query.isEmpty()) path else "$path?$query"
        // The pattern is matched by the browser, which doesn't know the quoting of Pattern.quote()
        val escaped = expected.replace(Regex("""[.*+?^${'$'}{}()|\[\]\\/]""")) { "\\${it.value}" }
        assertThat(page).hasURL(Pattern.compile("^[^?]*$escaped$"))
    }
}
