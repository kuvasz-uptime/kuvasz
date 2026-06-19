package com.kuvaszuptime.kuvasz.uitest.pages.push

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The push monitor list page at `/push-monitors`; the table is swapped into `#push-monitors-list` via HTMX.
class PushMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("push-monitor-row")

    val emptyState: Locator get() = page.getByText(Messages.noMonitors())

    fun navigate() {
        page.navigate("/push-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    fun openCreateModal(): PushMonitorFormModal {
        newMonitorButton.click()
        return PushMonitorFormModal(page)
    }

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("push-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("push-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
