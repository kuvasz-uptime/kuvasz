package com.kuvaszuptime.kuvasz.uitest.incident

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.incident.IncidentDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
@Property(name = "app-config.incident-notes-enabled", value = "false")
class IncidentNotesDisabledUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
) : UiTestSpec() {
    init {
        "the notes of an incident are shown, but they can't be added, edited or deleted" {
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository).id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )
            createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Still visible")

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", event.id)

            assertThat(details.note("Still visible")).isVisible()
            assertThat(details.addNoteButton).hasCount(0)
            assertThat(details.notes.getByTestId("incident-note-edit-button")).hasCount(0)
            assertThat(page.getByTestId("incident-note-modal")).hasCount(0)
        }
    }
}
