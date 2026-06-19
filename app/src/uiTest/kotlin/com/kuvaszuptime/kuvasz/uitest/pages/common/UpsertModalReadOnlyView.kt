package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

/**
 * Read-only view over the open monitor/status-page upsert modal (the "Configuration" dialog). In read-only mode the
 * fields display the entity's values but are disabled, and the footer offers only "Close" (the inherited
 * [saveButton] is absent).
 */
class UpsertModalReadOnlyView(page: Page) : ModalView(page) {

    // A form field by property name, e.g. `field("url")` -> `#url-input`.
    fun field(propName: String): Locator = modal.locator("#$propName-input")

    val closeButton: Locator get() = modal.getByTestId("modal-dismiss-button")
}
