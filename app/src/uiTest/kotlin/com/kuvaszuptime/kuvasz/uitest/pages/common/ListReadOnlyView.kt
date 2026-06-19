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
    private val listPath: String,
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

    // The per-row action buttons (toggle/delete) — none when the entity is read-only.
    fun actionButtonsIn(name: String): Locator = row(name).locator("button")

    // Opens the entity's detail page via its name link in the row.
    fun openDetails(name: String) {
        row(name).byRole(AriaRole.LINK, name).click()
    }
}
