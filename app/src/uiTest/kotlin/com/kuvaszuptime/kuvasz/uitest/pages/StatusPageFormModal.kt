package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for status pages (works for the create and update modals alike).
class StatusPageFormModal(page: Page) : ModalView(page) {

    val titleInput: Locator get() = modal.locator("#title-input")
    val slugInput: Locator get() = modal.locator("#slug-input")

    fun setTitle(value: String): StatusPageFormModal {
        titleInput.fill(value)
        return this
    }

    fun setSlug(value: String): StatusPageFormModal {
        slugInput.fill(value)
        return this
    }

    fun save() {
        // Alpine keeps Save disabled until the form validates; Playwright auto-waits for it to enable.
        saveButton.click()
    }
}
