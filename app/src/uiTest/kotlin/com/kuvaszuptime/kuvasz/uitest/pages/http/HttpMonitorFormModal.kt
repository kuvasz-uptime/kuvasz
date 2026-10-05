package com.kuvaszuptime.kuvasz.uitest.pages.http

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

/**
 * The Alpine.js-driven create/update modal for HTTP monitors. Works for both the "create" modal (list/dashboard) and
 * the "update" modal (detail page), since [ModalView] scopes to whichever dialog is open.
 */
class HttpMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#http-name-input")
    val urlInput: Locator get() = modal.locator("#http-url-input")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#http-uptimeCheckInterval-input")
    val crossOriginHeaderPropagationToggle: Locator
        get() = modal.locator("input[name='crossOriginHeaderPropagation']")

    /** Picks a proxy from the dropdown. Request settings have to be expanded first. */
    fun setProxy(value: String): HttpMonitorFormModal {
        pickFromDropdown(proxyField, value)
        return this
    }

    fun clearProxy(): HttpMonitorFormModal {
        clearProxyField()
        return this
    }

    fun setName(value: String): HttpMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setCategory(value: String): HttpMonitorFormModal {
        fillCategory(value)
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
        saveButton.click()
    }

    fun expandRequestSettings(): HttpMonitorFormModal {
        modal.getByTestId("accordion-toggle-http-monitor-request-settings").click()
        return this
    }

    fun expandEvaluationSettings(): HttpMonitorFormModal {
        modal.getByTestId("accordion-toggle-http-monitor-evaluation-settings").click()
        return this
    }

    // Custom request-headers component
    val newRequestHeaderKeyInput: Locator get() = modal.locator("#http-newRequestHeaderKey-input")
    val newRequestHeaderValueInput: Locator get() = modal.locator("#http-newRequestHeaderValue-input")
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

    fun requestHeaderRow(key: String): Locator =
        modal.getByTestId("header-row-requestHeaders").filter(Locator.FilterOptions().setHasText(key))

    fun removeRequestHeader(key: String) {
        requestHeaderRow(key).getByTestId("remove-header-button-requestHeaders").click()
    }
}
