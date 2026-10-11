package com.kuvaszuptime.kuvasz.uitest.pages.incident

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response

// An incident's details page at `/incidents/{type}/{eventId}`, telling its story on a timeline.
class IncidentDetailsPage(private val page: Page) {

    val heading: Locator = page.locator("#incident-detail-heading")

    val statusIndicator: Locator = page.getByTestId("incident-status-indicator")

    val addNoteButton: Locator = page.getByTestId("add-incident-note-button")

    val startedEvent: Locator = page.getByTestId("incident-started-event")

    val integrationsEvent: Locator = page.getByTestId("incident-integrations-event")

    val resolvedEvent: Locator = page.getByTestId("incident-resolved-event")

    val resolutionIntegrationsEvent: Locator = page.getByTestId("incident-resolution-integrations-event")

    // The notes on the timeline, in the order they are shown
    val notes: Locator = page.getByTestId("incident-note-event")

    val noteContents: List<String> get() = notes.getByTestId("incident-note-content").allInnerTexts()

    fun note(content: String): Locator = notes.filter(Locator.FilterOptions().setHasText(content))

    fun navigate(type: String, eventId: Long): Response? = page.navigate("/incidents/$type/$eventId")

    fun openNewNoteModal(): IncidentNoteModal {
        addNoteButton.click()
        return IncidentNoteModal(page)
    }

    fun openEditNoteModal(content: String): IncidentNoteModal {
        note(content).getByTestId("incident-note-edit-button").click()
        return IncidentNoteModal(page)
    }
}
