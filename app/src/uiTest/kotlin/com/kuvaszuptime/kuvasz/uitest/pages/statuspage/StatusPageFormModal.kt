package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
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

    // Decides whether the public page groups its monitors into their categories; it does not change what is selected.
    val displayCategoriesToggle: Locator
        get() = modal.getByTestId("display-categories-toggle").locator("input[type=checkbox]")

    fun setDisplayCategories(value: Boolean): StatusPageFormModal = apply {
        if (value) displayCategoriesToggle.check() else displayCategoriesToggle.uncheck()
    }

    fun save() {
        saveButton.click()
    }
}
