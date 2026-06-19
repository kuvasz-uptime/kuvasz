package com.kuvaszuptime.kuvasz.uitest.pages.icmp

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for ICMP monitors.
class IcmpMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val hostInput: Locator get() = modal.locator("#host-input")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#uptimeCheckInterval-input")

    fun setName(value: String): IcmpMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setHost(value: String): IcmpMonitorFormModal {
        hostInput.fill(value)
        return this
    }

    fun setUptimeCheckInterval(value: String): IcmpMonitorFormModal {
        uptimeCheckIntervalInput.fill(value)
        return this
    }

    fun save() {
        saveButton.click()
    }
}
