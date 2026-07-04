package com.kuvaszuptime.kuvasz.uitest.pages.maintenance

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A maintenance window's detail page at `/maintenance-windows/{id}`.
class MaintenanceWindowDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val configureButton: Locator get() = page.getByTestId("configure-button")

    val toggleButton: Locator get() = page.getByTestId("toggle-maintenance-window-button")

    // The live status dot in the header, refreshed via HTMX whenever the enabled state changes.
    fun statusIndicator(colorClass: String): Locator =
        page.locator("#maintenance-window-detail-heading .$colorClass")

    // A row of the details property table, located by its label (e.g. "Affected monitors").
    fun detailRow(label: String): Locator =
        page.locator("tr").filter(Locator.FilterOptions().setHasText(label))

    fun navigate(maintenanceWindowId: Long) {
        page.navigate("/maintenance-windows/$maintenanceWindowId")
    }

    fun openConfigureModal(): MaintenanceWindowFormModal {
        configureButton.click()
        return MaintenanceWindowFormModal(page)
    }
}
