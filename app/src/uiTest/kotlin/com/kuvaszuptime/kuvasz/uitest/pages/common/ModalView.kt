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

    // The main multi-select TomSelect of this modal: the accepted status codes on the HTTP monitor form, the
    // monitors on the status page and maintenance window ones. Pinned by its own wrapper, because those two forms
    // carry a second multi-select for the categories, see [categoriesField].
    private val multiSelectField: Locator get() = modal.getByTestId("multi-select")

    // The selected chips of the (single) TomSelect multi-select in this modal.
    val selectedOptions: Locator get() = multiSelectField.locator(".ts-control .item")

    // The options of the monitor multi-select, in the order the app offers them. TomSelect hides the `select` it is
    // built on, so their labels are read from the DOM instead of through the visibility-aware `allInnerTexts()`.
    val monitorOptions: Locator get() = multiSelectField.locator("select[multiple] option")

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
    val offeredCategories: List<String> get() = openedDropdownOptionsOf(categoryField)

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

    // The category multi-select of the status page and maintenance window forms, which selects whole categories of
    // monitors in addition to the ones picked one by one.
    protected val categoriesField: Locator get() = modal.getByTestId("categories-select")

    // The selected categories, as the chips TomSelect renders for them.
    val selectedCategories: Locator get() = categoriesField.locator(".ts-control .item")

    // The categories the field offers once its dropdown is open, in the order the app lists them.
    val offeredSelectableCategories: List<String> get() = openedDropdownOptionsOf(categoriesField)

    /**
     * Opens the dropdown of a category field and reads the options it offers. The first option is awaited, because
     * the options are fetched from the internal endpoint when the modal opens: reading them right after the click
     * would race the response. `allTextContents()` does not auto-wait on its own.
     */
    private fun openedDropdownOptionsOf(field: Locator): List<String> {
        field.locator(".ts-control").click()
        val options = field.locator(".ts-dropdown .option")
        options.first().waitFor()
        return options.allTextContents().map { it.trim() }
    }

    // Adds [category] to the selection, creating it when no existing category matches.
    fun selectCategory(category: String) {
        categoriesField.locator(".ts-control").click()
        val textbox = categoriesField.locator(".ts-control input")
        textbox.fill(category)
        // Enter takes the highlighted row, which is the matching category, or the "add new" one when there is none
        textbox.press("Enter")
    }

    // Clears every selected category through the clear button of the widget.
    fun clearCategories() {
        categoriesField.locator(".clear-button").click()
    }
}
