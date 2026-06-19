package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The status pages list at `/status-pages`; the table is swapped into `#status-page-list` via HTMX.
class StatusPageListPage(private val page: Page) {

    val addButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("status-page-row")

    fun navigate() {
        page.navigate("/status-pages")
    }

    fun openCreateModal(): StatusPageFormModal {
        addButton.click()
        return StatusPageFormModal(page)
    }

    fun rowByTitle(title: String): Locator = rows.filter(Locator.FilterOptions().setHasText(title))

    fun toggleVisibility(title: String) {
        rowByTitle(title).getByTestId("status-page-toggle-visibility-button").click()
    }

    fun publicIndicator(title: String): Locator = rowByTitle(title).getByTestId("status-page-public-indicator")

    fun privateIndicator(title: String): Locator = rowByTitle(title).getByTestId("status-page-private-indicator")
}
