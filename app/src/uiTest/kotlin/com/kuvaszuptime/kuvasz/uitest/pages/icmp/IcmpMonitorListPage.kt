package com.kuvaszuptime.kuvasz.uitest.pages.icmp

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The ICMP monitor list page at `/icmp-monitors`; the table is swapped into `#icmp-monitors-list` via HTMX.
class IcmpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("icmp-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/icmp-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-gray:has(.icon-tabler-tool)")

    fun openCreateModal(): IcmpMonitorFormModal {
        newMonitorButton.click()
        return IcmpMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): IcmpMonitorFormModal {
        rowByName(name).getByTestId("icmp-monitor-clone-button").click()
        return IcmpMonitorFormModal(page)
    }

    // Opens the given monitor's configuration from its row, returning the pre-filled update modal.
    fun configureMonitor(name: String): IcmpMonitorFormModal {
        configureButtonIn(name).click()
        return IcmpMonitorFormModal(page)
    }

    fun configureButtonIn(name: String): Locator = rowByName(name).getByTestId("icmp-monitor-configure-button")

    // Only rendered when the monitors are read-only, in place of every other action of the row.
    fun configurationButtonIn(name: String): Locator = rowByName(name).getByTestId("icmp-monitor-configuration-button")

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("icmp-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("icmp-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
