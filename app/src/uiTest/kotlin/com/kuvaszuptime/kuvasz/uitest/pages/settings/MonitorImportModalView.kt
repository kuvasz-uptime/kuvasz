package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.FilePayload

// Page object for the "Import monitors (YAML)" modal opened from the Settings backup dropdown.
class MonitorImportModalView(page: Page) : ModalView(page) {

    val warning: Locator get() = modal.getByText(Messages.monitorImportWarning())
    val submitButton: Locator get() = modal.getByTestId("monitor-import-submit-button")
    val result: Locator get() = modal.getByTestId("monitor-import-result")
    val error: Locator get() = modal.getByTestId("monitor-import-error")
    val cancelButton: Locator get() = modal.byRole(AriaRole.LINK, Messages.cancel())
    val closeButton: Locator get() = modal.byRole(AriaRole.LINK, Messages.close())
    val fileInput: Locator get() = modal.locator("#monitor-import-file-input")
    val dryRunToggle: Locator get() = modal.locator("input[name='dryRun']")

    fun waitUntilOpen() {
        submitButton.waitFor()
    }

    fun selectFile(fileName: String, content: ByteArray): MonitorImportModalView = apply {
        fileInput.setInputFiles(FilePayload(fileName, "application/yaml", content))
    }

    fun setDryRun(enabled: Boolean): MonitorImportModalView = apply {
        dryRunToggle.setChecked(enabled)
    }

    fun submit(): MonitorImportModalView = apply {
        submitButton.click()
    }

    fun cancel() {
        cancelButton.click()
    }

    fun close() {
        closeButton.click()
    }
}
