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

    // The multi-select TomSelect of this modal, scoped by the `multi` class TomSelect puts on its wrapper, so it
    // stays unambiguous next to the single-value category field of the monitor forms.
    private val multiSelectField: Locator get() = modal.locator(".ts-wrapper.multi")

    // The selected chips of the (single) TomSelect multi-select in this modal.
    val selectedOptions: Locator get() = multiSelectField.locator(".ts-control .item")

    // The options of the monitor multi-select, in the order the app offers them. TomSelect hides the `select` it is
    // built on, so their labels are read from the DOM instead of through the visibility-aware `allInnerTexts()`.
    val monitorOptions: Locator get() = modal.locator("select[multiple] option")

    val monitorOptionNames: List<String> get() = monitorOptions.allTextContents().map { it.trim() }

    // The checkboxes of the integrations accordion, which stays collapsed (but rendered) until it is opened.
    val integrationCheckboxes: Locator get() = modal.getByTestId("integration-checkbox")

    val integrationCheckboxNames: List<String> get() = integrationCheckboxes.allTextContents().map { it.trim() }

    // The category field of the monitor forms. It's scoped by its own wrapper, because a form can carry more than
    // one TomSelect: the HTTP one also has the accepted status codes.
    protected val categoryField: Locator get() = modal.getByTestId("category-select")

    // The selected category, as the chip TomSelect renders for it.
    val selectedCategory: Locator get() = categoryField.locator(".ts-control .item")

    // The categories the field offers once its dropdown is open, in the order the app lists them.
    val offeredCategories: List<String>
        get() {
            categoryField.locator(".ts-control").click()
            return categoryField.locator(".ts-dropdown .option").allTextContents().map { it.trim() }
        }

    // Picks [value] in the category field, creating it when no existing category matches. An empty [value] clears
    // the selection through the clear button of the widget.
    protected fun fillCategory(value: String) {
        if (value.isEmpty()) {
            categoryField.locator(".clear-button").click()
            return
        }
        categoryField.locator(".ts-control").click()
        val textbox = categoryField.locator(".ts-control input")
        textbox.fill(value)
        // Enter takes the highlighted row, which is the matching category, or the "add new" one when there is none
        textbox.press("Enter")
    }

    // Opens the modal's TomSelect dropdown and picks the option matching [optionText].
    fun selectOption(optionText: String) {
        multiSelectField.locator(".ts-control").click()
        multiSelectField.locator(".ts-dropdown .option")
            .filter(Locator.FilterOptions().setHasText(optionText))
            .first()
            .click()
    }
}
