package com.kuvaszuptime.kuvasz.uitest.pages.incident

import com.kuvaszuptime.kuvasz.uitest.pages.common.PagedListView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The incidents overview at `/incidents`; the list is loaded (and refreshed) by htmx into `#incident-list`, filtered
// by the filters of the accordion above it.
class IncidentListPage(private val page: Page) {

    val title: Locator = page.locator(".page-title")

    val emptyState: Locator = page.getByTestId("empty-state")

    val refreshButton: Locator = page.getByTestId("incident-list-refresh-button")

    // The filters, the sorting and the pages of the list
    val pagedList = PagedListView(page, "incident-list")

    // The rows of the list, one per incident
    val rows: Locator = page.locator("#incident-list tbody tr")

    fun detailsButtonOf(monitorName: String): Locator =
        rows.filter(Locator.FilterOptions().setHasText(monitorName)).getByTestId("incident-details-button")

    fun navigate(query: String = "") {
        page.navigate("/incidents$query")
    }

    fun expectQuery(query: String = "") = pagedList.expectQuery("/incidents", query)
}
