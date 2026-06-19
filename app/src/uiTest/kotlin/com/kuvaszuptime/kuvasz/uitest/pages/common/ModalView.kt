package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// Base for page objects scoped to the currently-open (`.modal.show`) modal dialog.
abstract class ModalView(protected val page: Page) {

    protected val modal: Locator get() = page.locator(".modal.show")

    val saveButton: Locator get() = modal.getByTestId("modal-save-button")

    // The Alpine.js client-side validation message carrying [message] (shown only while the field is invalid).
    fun validationError(message: String): Locator =
        modal.locator(".invalid-feedback").filter(Locator.FilterOptions().setHasText(message))

    // The selected chips of the (single) TomSelect multi-select in this modal.
    val selectedOptions: Locator get() = modal.locator(".ts-control .item")

    // Opens the modal's TomSelect dropdown and picks the option matching [optionText].
    fun selectOption(optionText: String) {
        modal.locator(".ts-control").click()
        modal.locator(".ts-dropdown .option")
            .filter(Locator.FilterOptions().setHasText(optionText))
            .first()
            .click()
    }
}
