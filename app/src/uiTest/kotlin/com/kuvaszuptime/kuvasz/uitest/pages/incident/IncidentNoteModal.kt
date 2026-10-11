package com.kuvaszuptime.kuvasz.uitest.pages.incident

import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.uitest.pages.common.ModalView
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Alpine.js-driven modal that adds a note to an incident, or edits and deletes one of its notes.
class IncidentNoteModal(page: Page) : ModalView(page) {

    val contentInput: Locator = modal.locator("#content-input")

    val timestampInput: Locator = modal.locator("#timestamp-input")

    val publicToggle: Locator = modal.locator("input[name='public']")

    val deleteButton: Locator = modal.getByTestId("incident-note-delete-button")

    private val confirmDeleteButton: Locator = modal.getByTestId("incident-note-delete-confirm-button")

    fun statusRadio(status: InvestigationStatus): Locator =
        modal.locator("input[name='status'][value='${status.name}']")

    // The selectgroup radios are visually hidden behind the pill styling, so they need a forced check
    fun selectStatus(status: InvestigationStatus): IncidentNoteModal = apply {
        statusRadio(status).check(Locator.CheckOptions().setForce(true))
    }

    fun setContent(value: String): IncidentNoteModal = apply { contentInput.fill(value) }

    // Typing is applied when the input changes (i.e. on blur or Enter), just like in the browser
    fun setTimestamp(value: String): IncidentNoteModal = apply {
        timestampInput.fill(value)
        timestampInput.dispatchEvent("change")
    }

    fun setPublic(value: Boolean): IncidentNoteModal = apply {
        if (value) publicToggle.check() else publicToggle.uncheck()
    }

    // Saving reloads the page in place, which `waitForURL` can't tell apart, so the reload request is awaited
    fun save() {
        page.waitForRequest({ it.isNavigationRequest }) { saveButton.click() }
    }

    // Deleting has to be confirmed in place, then it reloads the page in place too
    fun delete() {
        deleteButton.click()
        page.waitForRequest({ it.isNavigationRequest }) { confirmDeleteButton.click() }
    }
}
