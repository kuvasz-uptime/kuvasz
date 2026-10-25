package com.kuvaszuptime.kuvasz.uitest.pages.tcp

import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven create/update modal for TCP monitors.
class TcpMonitorFormModal(page: Page) : ModalView(page) {

    val nameInput: Locator get() = modal.locator("#tcp-name-input")
    val hostInput: Locator get() = modal.locator("#tcp-host-input")
    val portInput: Locator get() = modal.locator("#tcp-port-input")
    val portIncrementButton: Locator get() = portButton("increment")
    val portDecrementButton: Locator get() = portButton("decrement")
    val uptimeCheckIntervalInput: Locator get() = modal.locator("#tcp-uptimeCheckInterval-input")
    val latencyThresholdInput: Locator get() = modal.locator("#tcp-latencyThresholdMs-input")

    private fun portButton(action: String): Locator =
        portInput.locator("xpath=..").locator("[data-tblr-number-input-action=$action]")

    fun setName(value: String): TcpMonitorFormModal {
        nameInput.fill(value)
        return this
    }

    fun setCategory(value: String): TcpMonitorFormModal {
        fillCategory(value)
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
        openAccordion(EVALUATION_SETTINGS_ID)
        latencyThresholdInput.fill(value)
        return this
    }

    fun setProxy(value: String): TcpMonitorFormModal {
        pickFromDropdown(proxyField, value)
        return this
    }

    fun clearProxy(): TcpMonitorFormModal {
        clearProxyField()
        return this
    }

    val evaluationSettingsErrorIcon: Locator get() = accordionErrorIcon(EVALUATION_SETTINGS_ID)

    val portColumn: Locator get() = portInput.locator("xpath=ancestor::div[contains(@class, 'col-lg-3')]")
    val hostColumn: Locator get() = hostInput.locator("xpath=ancestor::div[contains(@class, 'col-lg-9')]")

    fun closeEvaluationSettings() {
        modal.getByTestId("accordion-toggle-$EVALUATION_SETTINGS_ID").click()
        assertAccordionClosed(EVALUATION_SETTINGS_ID)
    }

    fun save() {
        saveButton.click()
    }

    private companion object {
        const val EVALUATION_SETTINGS_ID = "tcp-monitor-evaluation-settings"
    }
}
