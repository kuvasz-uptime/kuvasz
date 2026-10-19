package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.PagedListView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The status pages list at `/status-pages`; the table is swapped into `#status-page-list` via HTMX.
class StatusPageListPage(private val page: Page) {

    val addButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("status-page-row")

    // The row of the default status page, which is pinned to the top of the first page
    val defaultRow: Locator get() = page.getByTestId("default-status-page-row")

    // Shown instead of its preview link when it's not available, because there are too many enabled monitors
    val defaultRowUnavailableBadge: Locator get() = defaultRow.getByTestId("default-status-page-unavailable-badge")

    val defaultRowUnavailablePreview: Locator get() = defaultRow.getByTestId("default-status-page-preview-unavailable")

    val defaultRowPreviewLink: Locator get() = defaultRow.locator("a[href='/status/']")

    // The filters, the sorting and the pages of the list
    val pagedList = PagedListView(page, "status-page-list")

    val titles: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    fun navigate() {
        page.navigate("/status-pages")
    }

    fun openCreateModal(): StatusPageFormModal {
        addButton.click()
        return StatusPageFormModal(page)
    }

    fun rowByTitle(title: String): Locator = rows.filter(Locator.FilterOptions().setHasText(title))

    // Opens the given status page's configuration from its row, returning the pre-filled update modal.
    fun configureStatusPage(title: String): StatusPageFormModal {
        configureButtonIn(title).click()
        return StatusPageFormModal(page)
    }

    fun configureButtonIn(title: String): Locator = rowByTitle(title).getByTestId("status-page-configure-button")

    // Clones the given status page, returning the pre-filled create modal.
    fun cloneStatusPage(title: String): StatusPageFormModal {
        rowByTitle(title).getByTestId("status-page-clone-button").click()
        return StatusPageFormModal(page)
    }

    // Only rendered when the status pages are read-only, in place of every other action of the row.
    fun configurationButtonIn(title: String): Locator =
        rowByTitle(title).getByTestId("status-page-configuration-button")

    fun toggleVisibility(title: String) {
        rowByTitle(title).getByTestId("status-page-toggle-visibility-button").click()
    }

    fun categoriesCell(title: String): Locator = rowByTitle(title).getByTestId("status-page-categories")

    fun publicIndicator(title: String): Locator = rowByTitle(title).getByTestId("status-page-public-indicator")

    fun privateIndicator(title: String): Locator = rowByTitle(title).getByTestId("status-page-private-indicator")
}
