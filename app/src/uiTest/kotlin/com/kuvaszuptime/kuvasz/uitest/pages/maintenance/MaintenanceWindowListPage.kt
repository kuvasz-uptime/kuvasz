package com.kuvaszuptime.kuvasz.uitest.pages.maintenance

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The maintenance windows list at `/maintenance-windows`; the table is HTMX-swapped into `#maintenance-window-list`.
class MaintenanceWindowListPage(private val page: Page) {

    val addButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("maintenance-window-row")

    // Replaces the table entirely while there isn't a single maintenance window
    val emptyState: Locator get() = page.getByTestId("empty-state")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    fun navigate() {
        page.navigate("/maintenance-windows")
    }

    fun openCreateModal(): MaintenanceWindowFormModal {
        addButton.click()
        return MaintenanceWindowFormModal(page)
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // Opens the given window's configuration from its row, returning the pre-filled update modal.
    fun configureMaintenanceWindow(name: String): MaintenanceWindowFormModal {
        configureButtonIn(name).click()
        return MaintenanceWindowFormModal(page)
    }

    fun configureButtonIn(name: String): Locator = rowByName(name).getByTestId("maintenance-window-configure-button")

    // Clones the given window, returning the pre-filled create modal.
    fun cloneMaintenanceWindow(name: String): MaintenanceWindowFormModal {
        rowByName(name).getByTestId("maintenance-window-clone-button").click()
        return MaintenanceWindowFormModal(page)
    }

    // Only rendered when the maintenance windows are read-only, in place of every other action of the row.
    fun configurationButtonIn(name: String): Locator =
        rowByName(name).getByTestId("maintenance-window-configuration-button")

    // The "Monitors" column cell of a row: either the global-scope badge or the affected-monitor count.
    fun monitorsCell(name: String): Locator = rowByName(name).getByTestId("maintenance-window-monitors")

    fun categoriesCell(name: String): Locator = rowByName(name).getByTestId("maintenance-window-categories")

    // The icon-only status badge, matched by its tooltip text. Bootstrap moves the rendered `title` into
    // `data-bs-original-title` when it takes the element over, so both have to be considered.
    fun statusBadge(name: String, status: String): Locator = rowByName(name).locator(
        "[data-testid=maintenance-window-status][title=\"$status\"], " +
            "[data-testid=maintenance-window-status][data-bs-original-title=\"$status\"]"
    )

    fun toggle(name: String) {
        rowByName(name).getByTestId("maintenance-window-toggle-button").click()
    }
}
