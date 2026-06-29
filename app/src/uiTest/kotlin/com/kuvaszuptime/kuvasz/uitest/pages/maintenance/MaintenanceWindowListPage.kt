package com.kuvaszuptime.kuvasz.uitest.pages.maintenance

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The maintenance windows list at `/maintenance-windows`; the table is HTMX-swapped into `#maintenance-window-list`.
class MaintenanceWindowListPage(private val page: Page) {

    val addButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("maintenance-window-row")

    fun navigate() {
        page.navigate("/maintenance-windows")
    }

    fun openCreateModal(): MaintenanceWindowFormModal {
        addButton.click()
        return MaintenanceWindowFormModal(page)
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The "Monitors" column cell of a row: either the global-scope badge or the affected-monitor count.
    fun monitorsCell(name: String): Locator = rowByName(name).getByTestId("maintenance-window-monitors")

    fun toggle(name: String) {
        rowByName(name).getByTestId("maintenance-window-toggle-button").click()
    }
}
