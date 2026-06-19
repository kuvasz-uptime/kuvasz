package com.kuvaszuptime.kuvasz.uitest.pages.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The ICMP monitor list page at `/icmp-monitors`; the table is swapped into `#icmp-monitors-list` via HTMX.
class IcmpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

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

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("icmp-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("icmp-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
