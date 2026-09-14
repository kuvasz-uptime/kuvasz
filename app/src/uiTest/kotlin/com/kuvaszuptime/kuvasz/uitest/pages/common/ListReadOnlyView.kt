package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

/**
 * Read-only view over a monitor or status-page list. They share the same structure (read-only badge, no "add" button,
 * rows without action buttons)
 */
class ListReadOnlyView(
    private val page: Page,
    val listPath: String,
) {

    // The lock badge rendered in the header when the type is read-only (YAML-configured).
    val readOnlyBadge: Locator get() = page.getByTestId("read-only-badge")

    // Present only when the type is editable; omitted entirely in read-only mode.
    val addButton: Locator get() = page.getByTestId("add-new-button")

    fun navigate() {
        page.navigate(listPath)
    }

    fun row(name: String): Locator =
        page.locator("tbody tr").filter(Locator.FilterOptions().setHasText(name))

    // The per-row action buttons: none for a read-only status page or maintenance window, and only the view-only
    // configuration one for a read-only monitor.
    fun actionButtonsIn(name: String): Locator = row(name).locator("button")

    // The view-only configuration button of a read-only monitor's row, e.g. `http-monitor-configuration-button`.
    fun configurationButtonIn(name: String): Locator =
        row(name).locator("[data-testid$='-monitor-configuration-button']")

    // Opens the read-only configuration modal of a monitor straight from its row, without leaving the list.
    fun openConfigurationModal(name: String): UpsertModalReadOnlyView {
        configurationButtonIn(name).click()
        return UpsertModalReadOnlyView(page)
    }

    // Opens the entity's detail page via its name link in the row.
    fun openDetails(name: String) {
        row(name).byRole(AriaRole.LINK, name).click()
    }
}
