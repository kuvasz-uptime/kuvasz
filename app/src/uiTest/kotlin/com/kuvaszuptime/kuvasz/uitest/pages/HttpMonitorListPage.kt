package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The HTTP & SSL monitor list page at `/http-monitors`; the table is swapped into `#http-monitors-list` via HTMX.
class HttpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.addNewMonitor())

    val rows: Locator get() = page.getByTestId("http-monitor-row")

    val emptyState: Locator get() = page.getByText(Messages.noMonitors())

    fun navigate() {
        page.navigate("/http-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    fun openCreateModal(): HttpMonitorFormModal {
        newMonitorButton.click()
        return HttpMonitorFormModal(page)
    }

    // Clicks the pause/resume toggle on the named row (the list re-renders via HTMX afterwards).
    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-toggle-button").click()
    }

    // Clicks the trash icon on the named row and confirms the deletion in the modal.
    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("http-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
