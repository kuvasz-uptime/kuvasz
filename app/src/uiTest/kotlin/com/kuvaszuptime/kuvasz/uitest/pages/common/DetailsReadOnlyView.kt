package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

/**
 * Read-only view over a monitor or status-page detail page. In read-only mode the editable "Configure" button is
 * replaced by a view-only "Configuration" one that opens the upsert modal read-only.
 */
class DetailsReadOnlyView(private val page: Page) {

    // Present only in read-only mode.
    val configurationButton: Locator get() = page.getByTestId("configuration-button")

    // Present only when the entity is editable.
    val configureButton: Locator get() = page.getByTestId("configure-button")

    fun openConfigurationModal(): UpsertModalReadOnlyView {
        configurationButton.click()
        return UpsertModalReadOnlyView(page)
    }
}
