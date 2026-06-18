package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The push monitor list page at `/push-monitors`; the table is swapped into `#push-monitors-list` via HTMX.
class PushMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.addNewMonitor())

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

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("push-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
