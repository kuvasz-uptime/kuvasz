package com.kuvaszuptime.kuvasz.uitest.pages.http

import com.kuvaszuptime.kuvasz.uitest.pages.common.PagedListView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// The HTTP & SSL monitor list page at `/http-monitors`; the table is swapped into `#http-monitors-list` via HTMX.
class HttpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("http-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    val refreshButton: Locator get() = page.getByTestId("http-monitor-refresh-button")

    fun navigate() {
        page.navigate("/http-monitors")
    }

    /** Opens the list straight on a category, the way a shared permalink would. */
    fun navigateToCategory(category: String) {
        page.navigate("/http-monitors?category=${URLEncoder.encode(category, StandardCharsets.UTF_8)}")
    }

    // The filters, the sorting and the pages of the list
    val pagedList = PagedListView(page, "http-monitors-list")

    // The category filter lives in the filter accordion above the list, so a refresh never touches it
    val categoryOptions: List<String>
        get() = pagedList.filter("category").locator("option").allTextContents().map { it.trim() }

    val selectedCategory: String get() = pagedList.chosenLabel("category").trim()

    fun filterByCategory(label: String) {
        if (!pagedList.filter("category").isVisible) pagedList.openFilters()
        pagedList.choose("category", label)
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-secondary:has(.icon-tabler-tool)")

    fun openCreateModal(): HttpMonitorFormModal {
        newMonitorButton.click()
        return HttpMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): HttpMonitorFormModal {
        rowByName(name).getByTestId("http-monitor-clone-button").click()
        return HttpMonitorFormModal(page)
    }

    // Opens the given monitor's configuration from its row, returning the pre-filled update modal.
    fun configureMonitor(name: String): HttpMonitorFormModal {
        configureButtonIn(name).click()
        return HttpMonitorFormModal(page)
    }

    fun configureButtonIn(name: String): Locator = rowByName(name).getByTestId("http-monitor-configure-button")

    // Only rendered when the monitors are read-only, in place of every other action of the row.
    fun configurationButtonIn(name: String): Locator = rowByName(name).getByTestId("http-monitor-configuration-button")

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
