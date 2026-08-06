package com.kuvaszuptime.kuvasz.uitest.pages.dns

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The DNS monitor list page at `/dns-monitors`; the table is swapped into `#dns-monitors-list` via HTMX.
class DnsMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("dns-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/dns-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-gray:has(.icon-tabler-tool)")

    fun openCreateModal(): DnsMonitorFormModal {
        newMonitorButton.click()
        return DnsMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): DnsMonitorFormModal {
        rowByName(name).getByTestId("dns-monitor-clone-button").click()
        return DnsMonitorFormModal(page)
    }

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("dns-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("dns-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
