package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

/**
 * The Alpine.js-driven create/update modal for HTTP monitors. Works for both the "create" modal (list/dashboard) and
 * the "update" modal (detail page), since [ModalView] scopes to whichever dialog is open.
 */
class HttpMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val urlInput: Locator get() = modal.locator("#url-input")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#uptimeCheckInterval-input")

    fun setName(value: String): HttpMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setUrl(value: String): HttpMonitorFormModal {
        urlInput.fill(value)
        return this
    }

    fun setUptimeCheckInterval(value: String): HttpMonitorFormModal {
        uptimeCheckIntervalInput.fill(value)
        return this
    }

    fun save() {
        // Alpine keeps Save disabled until the form validates; Playwright auto-waits for it to enable.
        saveButton.click()
    }

    // Expands the "Request Settings" accordion section (which holds the request-headers component).
    fun expandRequestSettings(): HttpMonitorFormModal {
        modal.byRole(AriaRole.BUTTON, Messages.requestSettingsLabel()).click()
        return this
    }

    // Expands the "Evaluation Settings" accordion section (which holds the accepted-status-codes multi-select).
    fun expandEvaluationSettings(): HttpMonitorFormModal {
        modal.byRole(AriaRole.BUTTON, Messages.evaluationSettingsLabel()).click()
        return this
    }

    // --- Custom request-headers component ---

    val newRequestHeaderKeyInput: Locator get() = modal.locator("#newRequestHeaderKey-input")
    val newRequestHeaderValueInput: Locator get() = modal.locator("#newRequestHeaderValue-input")
    val addRequestHeaderButton: Locator get() = modal.getByTestId("add-header-button-requestHeaders")

    fun setNewRequestHeaderKey(value: String): HttpMonitorFormModal {
        newRequestHeaderKeyInput.fill(value)
        return this
    }

    fun setNewRequestHeader(key: String, value: String): HttpMonitorFormModal {
        newRequestHeaderKeyInput.fill(key)
        newRequestHeaderValueInput.fill(value)
        return this
    }

    fun addRequestHeader() {
        addRequestHeaderButton.click()
    }

    // The header row (in the request-headers table) whose key is [key].
    fun requestHeaderRow(key: String): Locator =
        modal.locator("tr").filter(Locator.FilterOptions().setHasText(key))

    fun removeRequestHeader(key: String) {
        requestHeaderRow(key).locator("button").click()
    }
}
