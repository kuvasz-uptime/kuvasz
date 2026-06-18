package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

/**
 * Read-only view over a monitor or status-page detail page. In read-only mode the editable "Configure" button is
 * replaced by a view-only "Configuration" one that opens the upsert modal read-only.
 */
class DetailsReadOnlyView(private val page: Page) {

    // Present only in read-only mode.
    val configurationButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.configuration())

    // Present only when the entity is editable.
    val configureButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.configure())

    fun openConfigurationModal(): UpsertModalReadOnlyView {
        configurationButton.click()
        return UpsertModalReadOnlyView(page)
    }
}
