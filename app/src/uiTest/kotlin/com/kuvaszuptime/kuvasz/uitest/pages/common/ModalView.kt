package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.WaitForSelectorState

// Base for page objects scoped to the currently-open (`.modal.show`) modal dialog.
abstract class ModalView(protected val page: Page) {

    protected val modal: Locator get() = page.locator(".modal.show")

    val saveButton: Locator get() = modal.getByTestId("modal-save-button")

    val dismissButton: Locator get() = modal.getByTestId("modal-dismiss-button")

    // The form-level error the server reported for a save the client-side validation let through.
    val formError: Locator get() = modal.getByTestId("modal-form-error")

    // Closes the modal without saving and waits for it to disappear, so a subsequent re-open sees a settled UI.
    fun dismiss() {
        dismissButton.click()
        page.locator(".modal.show").waitFor(Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED))
    }

    // The Alpine.js client-side validation message carrying [message] (shown only while the field is invalid).
    fun validationError(message: String): Locator =
        modal.locator(".invalid-feedback").filter(Locator.FilterOptions().setHasText(message))

    // The selected chips of the (single) TomSelect multi-select in this modal.
    val selectedOptions: Locator get() = modal.locator(".ts-control .item")

    // The options of the monitor multi-select, in the order the app offers them. TomSelect hides the `select` it is
    // built on, so their labels are read from the DOM instead of through the visibility-aware `allInnerTexts()`.
    val monitorOptions: Locator get() = modal.locator("select[multiple] option")

    val monitorOptionNames: List<String> get() = monitorOptions.allTextContents().map { it.trim() }

    // The checkboxes of the integrations accordion, which stays collapsed (but rendered) until it is opened.
    val integrationCheckboxes: Locator get() = modal.getByTestId("integration-checkbox")

    val integrationCheckboxNames: List<String> get() = integrationCheckboxes.allTextContents().map { it.trim() }

    // Opens the modal's TomSelect dropdown and picks the option matching [optionText].
    fun selectOption(optionText: String) {
        modal.locator(".ts-control").click()
        modal.locator(".ts-dropdown .option")
            .filter(Locator.FilterOptions().setHasText(optionText))
            .first()
            .click()
    }
}
