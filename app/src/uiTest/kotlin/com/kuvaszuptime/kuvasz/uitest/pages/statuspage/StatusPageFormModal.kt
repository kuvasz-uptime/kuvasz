package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for status pages (works for the create and update modals alike).
class StatusPageFormModal(page: Page) : ModalView(page) {

    val titleInput: Locator get() = modal.locator("#title-input")
    val slugInput: Locator get() = modal.locator("#slug-input")
    val publicToggle: Locator get() = modal.locator("input[name='public']")

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

    // The radio of a gray palette, by its API value (e.g. "SLATE")
    fun themeBaseRadio(value: String): Locator =
        modal.getByTestId("theme-base-picker").locator("input[value='$value']")

    // The radios are visually hidden behind their pills, so they need a forced check.
    fun pickThemeBase(value: String): StatusPageFormModal = apply {
        themeBaseRadio(value).check(Locator.CheckOptions().setForce(true))
    }

    fun save() {
        saveButton.click()
    }
}
