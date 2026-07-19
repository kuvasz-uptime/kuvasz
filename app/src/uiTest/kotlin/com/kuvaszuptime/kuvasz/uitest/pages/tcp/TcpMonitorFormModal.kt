package com.kuvaszuptime.kuvasz.uitest.pages.tcp

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for TCP monitors.
class TcpMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#name-input")
    val hostInput: Locator get() = modal.locator("#host-input")
    val portInput: Locator get() = modal.locator("#port-input")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#uptimeCheckInterval-input")
    val latencyThresholdInput: Locator get() = modal.locator("#latencyThresholdMs-input")

    fun setName(value: String): TcpMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setHost(value: String): TcpMonitorFormModal {
        hostInput.fill(value)
        return this
    }

    fun setPort(value: String): TcpMonitorFormModal {
        portInput.fill(value)
        return this
    }

    fun setUptimeCheckInterval(value: String): TcpMonitorFormModal {
        uptimeCheckIntervalInput.fill(value)
        return this
    }

    fun setLatencyThreshold(value: String): TcpMonitorFormModal {
        latencyThresholdInput.fill(value)
        return this
    }

    fun save() {
        saveButton.click()
    }
}
