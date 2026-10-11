package com.kuvaszuptime.kuvasz.uitest.pages.incident

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The incidents overview at `/incidents`; the list is loaded (and refreshed) by htmx, filtered by the period and the
// type picked in its toolbar.
class IncidentListPage(private val page: Page) {

    val title: Locator = page.locator(".page-title")

    val emptyState: Locator = page.getByTestId("empty-state")

    val periodFilter: Locator = page.getByTestId("incident-period-filter")

    val typeFilter: Locator = page.getByTestId("incident-type-filter")

    val count: Locator = page.getByTestId("incident-count")

    val refreshButton: Locator = page.getByTestId("incident-list-refresh-button")

    // The rows of the list, one per incident
    val rows: Locator = page.locator("tbody tr")

    fun detailsButtonOf(monitorName: String): Locator =
        rows.filter(Locator.FilterOptions().setHasText(monitorName)).getByTestId("incident-details-button")

    fun navigate(query: String = "") {
        page.navigate("/incidents$query")
    }

    // Both filters navigate to the URL with the new value, which the specs assert on
    fun filterByType(value: String) {
        typeFilter.selectOption(value)
    }

    fun filterByPeriod(value: String) {
        periodFilter.selectOption(value)
    }
}
