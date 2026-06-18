package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for push monitors (the client secret is auto-generated on open).
class PushMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val heartbeatIntervalInput: Locator get() = modal.locator("#heartbeatInterval-input")

    fun setName(value: String): PushMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setHeartbeatInterval(value: String): PushMonitorFormModal {
        heartbeatIntervalInput.fill(value)
        return this
    }

    fun save() {
        // Alpine keeps Save disabled until the form validates; Playwright auto-waits for it to enable.
        saveButton.click()
    }
}
