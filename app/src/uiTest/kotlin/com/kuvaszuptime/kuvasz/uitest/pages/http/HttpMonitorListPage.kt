package com.kuvaszuptime.kuvasz.uitest.pages.http

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.SelectOption

// The HTTP & SSL monitor list page at `/http-monitors`; the table is swapped into `#http-monitors-list` via HTMX.
class HttpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("http-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/http-monitors")
    }

    /** Opens the list straight on a category, the way a shared permalink would. */
    fun navigateToCategory(category: String) {
        page.navigate("/http-monitors?category=$category")
    }

    // The category filter above the table. It lives outside the HTMX-swapped fragment, so a refresh never touches it.
    private val categoryFilter: Locator get() = page.getByTestId("category-filter").locator("select")

    val categoryOptions: List<String>
        get() = categoryFilter.locator("option").allTextContents().map { it.trim() }

    val selectedCategory: String
        get() = categoryFilter.locator("option[selected]").first().innerText().trim()

    /**
     * Picks an option by its visible label. The select navigates the whole page, and the option's value is exactly
     * the query suffix it navigates to, so the wait can key on that instead of racing the navigation.
     */
    fun filterByCategory(label: String) {
        val option = categoryFilter.locator("option").filter(Locator.FilterOptions().setHasText(label)).first()
        val expectedSuffix = "/http-monitors" + option.getAttribute("value").orEmpty()
        categoryFilter.selectOption(SelectOption().setLabel(label))
        page.waitForURL { url -> url.endsWith(expectedSuffix) }
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-gray:has(.icon-tabler-tool)")

    fun openCreateModal(): HttpMonitorFormModal {
        newMonitorButton.click()
        return HttpMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): HttpMonitorFormModal {
        rowByName(name).getByTestId("http-monitor-clone-button").click()
        return HttpMonitorFormModal(page)
    }

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
