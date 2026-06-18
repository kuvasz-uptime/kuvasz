package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The ICMP monitor list page at `/icmp-monitors`; the table is swapped into `#icmp-monitors-list` via HTMX.
class IcmpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.addNewMonitor())

    val rows: Locator get() = page.getByTestId("icmp-monitor-row")

    val emptyState: Locator get() = page.getByText(Messages.noMonitors())

    fun navigate() {
        page.navigate("/icmp-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    fun openCreateModal(): IcmpMonitorFormModal {
        newMonitorButton.click()
        return IcmpMonitorFormModal(page)
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("icmp-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
