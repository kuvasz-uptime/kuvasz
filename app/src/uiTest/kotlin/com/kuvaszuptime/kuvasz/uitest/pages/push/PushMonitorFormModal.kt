package com.kuvaszuptime.kuvasz.uitest.pages.push

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for push monitors (the client secret is auto-generated on open).
class PushMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val heartbeatIntervalInput: Locator get() = modal.locator("#heartbeatInterval-input")

    val clientSecretInput: Locator get() = modal.locator("input[x-model='clientSecret']")

    val clientSecret: String get() = clientSecretInput.inputValue()

    fun setName(value: String): PushMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setHeartbeatInterval(value: String): PushMonitorFormModal {
        heartbeatIntervalInput.fill(value)
        return this
    }

    fun setClientSecret(value: String): PushMonitorFormModal {
        clientSecretInput.fill(value)
        return this
    }

    fun save() {
        saveButton.click()
    }
}
