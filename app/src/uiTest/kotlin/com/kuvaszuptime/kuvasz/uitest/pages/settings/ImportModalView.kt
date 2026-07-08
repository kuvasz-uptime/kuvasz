package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.FilePayload

/**
 * Shared page object for the YAML import modals opened from the Settings backup dropdown. Concrete modals only differ
 * in their testId prefix, file input id and warning text.
 */
open class ImportModalView(
    page: Page,
    private val testIdPrefix: String,
    private val fileInputId: String,
    private val warningText: String,
) : ModalView(page) {

    val warning: Locator get() = modal.getByText(warningText)
    val submitButton: Locator get() = modal.getByTestId("$testIdPrefix-submit-button")
    val result: Locator get() = modal.getByTestId("$testIdPrefix-result")
    val error: Locator get() = modal.getByTestId("$testIdPrefix-error")
    val cancelButton: Locator get() = modal.byRole(AriaRole.LINK, Messages.cancel())
    val closeButton: Locator get() = modal.byRole(AriaRole.LINK, Messages.close())
    val fileInput: Locator get() = modal.locator("#$fileInputId")
    val dryRunToggle: Locator get() = modal.locator("input[name='dryRun']")

    fun waitUntilOpen() {
        submitButton.waitFor()
    }

    fun selectFile(fileName: String, content: ByteArray): ImportModalView = apply {
        fileInput.setInputFiles(FilePayload(fileName, "application/yaml", content))
    }

    fun setDryRun(enabled: Boolean): ImportModalView = apply {
        dryRunToggle.setChecked(enabled)
    }

    fun submit(): ImportModalView = apply {
        submitButton.click()
    }

    fun cancel() {
        cancelButton.click()
    }

    fun close() {
        closeButton.click()
    }
}
