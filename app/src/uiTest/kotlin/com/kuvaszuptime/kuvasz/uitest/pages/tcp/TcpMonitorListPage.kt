package com.kuvaszuptime.kuvasz.uitest.pages.tcp

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The TCP monitor list page at `/tcp-monitors`; the table is swapped into `#tcp-monitors-list` via HTMX.
class TcpMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("tcp-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/tcp-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-gray:has(.icon-tabler-tool)")

    fun openCreateModal(): TcpMonitorFormModal {
        newMonitorButton.click()
        return TcpMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): TcpMonitorFormModal {
        rowByName(name).getByTestId("tcp-monitor-clone-button").click()
        return TcpMonitorFormModal(page)
    }

    // Opens the given monitor's configuration from its row, returning the pre-filled update modal.
    fun configureMonitor(name: String): TcpMonitorFormModal {
        configureButtonIn(name).click()
        return TcpMonitorFormModal(page)
    }

    fun configureButtonIn(name: String): Locator = rowByName(name).getByTestId("tcp-monitor-configure-button")

    // Only rendered when the monitors are read-only, in place of every other action of the row.
    fun configurationButtonIn(name: String): Locator = rowByName(name).getByTestId("tcp-monitor-configuration-button")

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("tcp-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("tcp-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
