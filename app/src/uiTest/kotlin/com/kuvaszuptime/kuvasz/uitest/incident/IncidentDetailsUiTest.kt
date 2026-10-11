package com.kuvaszuptime.kuvasz.uitest.incident

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.incident.IncidentDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.regex.Pattern

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IncidentDetailsUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val incidentNoteRepository: IncidentNoteRepository,
) : UiTestSpec() {
    init {
        fun createIncident(endedAt: Boolean = false): Long {
            val now = getCurrentTimestamp()
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Incident Monitor")
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(1),
                endedAt = if (endedAt) now else null,
                error = "Connection refused",
                integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
            )
            if (endedAt) {
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now,
                    endedAt = null,
                    integrations = arrayOf(IntegrationID(IntegrationType.PAGERDUTY, "on-call")),
                )
            }
            return event.id
        }

        "the timeline tells the story of a resolved incident, which is never refreshed" {
            val eventId = createIncident(endedAt = true)
            createIncidentNote(
                dslContext,
                IncidentType.HTTP,
                eventId,
                status = InvestigationStatus.IDENTIFIED,
                content = "The <b>database</b> is down",
                public = true,
            )

            val page = newPage()
            page.clock().install()
            val refreshes = mutableListOf<String>()
            page.onRequest { request -> if ("/fragments/details/" in request.url()) refreshes.add(request.url()) }
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)

            assertThat(details.heading).containsText("Incident Monitor")
            assertThat(details.statusIndicator).hasClass(Pattern.compile(".*status-green.*"))
            assertThat(details.startedEvent).containsText("Connection refused")
            assertThat(details.integrationsEvent).containsText("slack:ops")
            assertThat(details.resolvedEvent).isVisible()
            assertThat(details.resolutionIntegrationsEvent).containsText("pagerduty:on-call")
            assertThat(details.note("database")).containsText(Messages.investigationStatusIdentified())
            assertThat(details.note("database")).containsText(Messages.notePublic())
            // The content is plain text, the markup in it is shown as it is
            details.noteContents shouldBe listOf("The <b>database</b> is down")

            page.clock().runFor(NO_REFRESH_PERIOD_MILLIS)
            // Nothing could be awaited here, as the absence of a request is checked
            page.waitForTimeout(NO_REFRESH_GRACE_MILLIS)
            refreshes shouldBe emptyList()
        }

        "the timeline is chronological, with the incident's own nodes first at the same time" {
            val now = getCurrentTimestamp()
            val startedAt = now.minusHours(2)
            val monitor = createHttpMonitor(httpMonitorRepository)
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = startedAt,
                endedAt = now.minusHours(1),
                integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(1),
                endedAt = null,
                integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
            )
            // Created in a different order than their times, to prove that the times decide
            createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "After", createdAt = now)
            createIncidentNote(
                dslContext,
                IncidentType.HTTP,
                event.id,
                content = "Before",
                timestamp = startedAt.minusMinutes(NOTE_OFFSET_MINUTES),
            )
            createIncidentNote(
                dslContext,
                IncidentType.HTTP,
                event.id,
                content = "During",
                timestamp = startedAt.plusMinutes(NOTE_OFFSET_MINUTES),
            )
            createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "AtStart", timestamp = startedAt)

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", event.id)

            assertThat(details.resolvedEvent).isVisible()
            details.timelineEventIds shouldBe listOf(
                "incident-note-event",
                "incident-started-event",
                "incident-integrations-event",
                "incident-note-event",
                "incident-note-event",
                "incident-resolved-event",
                "incident-resolution-integrations-event",
                "incident-note-event",
            )
            details.noteContents shouldBe listOf("Before", "AtStart", "During", "After")
        }

        "the timeline has nothing about the integrations, when none of them were notified" {
            val now = getCurrentTimestamp()
            val monitor = createHttpMonitor(httpMonitorRepository)
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(1),
                endedAt = now,
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now,
                endedAt = null,
            )

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", event.id)

            assertThat(details.resolvedEvent).isVisible()
            details.timelineEventIds shouldBe listOf("incident-started-event", "incident-resolved-event")
        }

        "an ongoing incident is resolved on the page without reloading it, which stops the refreshing" {
            val now = getCurrentTimestamp()
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Incident Monitor")
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(1),
                endedAt = null,
            )

            val page = newPage()
            page.clock().install()
            val details = IncidentDetailsPage(page)
            details.navigate("http", event.id)
            assertThat(details.statusIndicator).hasClass(Pattern.compile(".*status-indicator-animated.*"))
            assertThat(details.resolvedEvent).hasCount(0)

            createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Restarted the database")
            dslContext.update(HTTP_UPTIME_EVENT)
                .set(HTTP_UPTIME_EVENT.ENDED_AT, now)
                .where(HTTP_UPTIME_EVENT.ID.eq(event.id))
                .execute()
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now,
                endedAt = null,
                integrations = arrayOf(IntegrationID(IntegrationType.PAGERDUTY, "on-call")),
            )
            page.clock().runFor(REFRESH_INTERVAL_MILLIS)

            assertThat(details.statusIndicator).hasClass(Pattern.compile(".*status-green.*"))
            assertThat(details.resolvedEvent).isVisible()
            assertThat(details.resolutionIntegrationsEvent).containsText("pagerduty:on-call")
            assertThat(details.note("Restarted the database")).isVisible()

            val refreshes = mutableListOf<String>()
            page.onRequest { request -> if ("/fragments/details/" in request.url()) refreshes.add(request.url()) }
            page.clock().runFor(NO_REFRESH_PERIOD_MILLIS)
            // Nothing could be awaited here, as the absence of a request is checked
            page.waitForTimeout(NO_REFRESH_GRACE_MILLIS)
            refreshes shouldBe emptyList()
        }

        "a public note can be added to an incident" {
            val eventId = createIncident()

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)
            assertThat(details.statusIndicator).hasClass(Pattern.compile(".*status-indicator-animated.*"))

            details.openNewNoteModal()
                .selectStatus(InvestigationStatus.IDENTIFIED)
                .setContent("The database is down\nWe are restarting it")
                .setPublic(true)
                .save()

            val note = details.note("The database is down")
            assertThat(note).containsText(Messages.investigationStatusIdentified())
            assertThat(note).containsText(Messages.notePublic())
            // The line breaks of the content are kept
            details.noteContents shouldBe listOf("The database is down\nWe are restarting it")
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, eventId).single().let { saved ->
                saved.status shouldBe InvestigationStatus.IDENTIFIED
                saved.public shouldBe true
                saved.timestamp shouldBe null
            }
        }

        "a note needs a content, and a valid timestamp if one is given" {
            val eventId = createIncident()

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)

            val modal = details.openNewNoteModal()
            modal.setTimestamp("2030-01-05")
            modal.saveButton.click()

            assertThat(modal.validationError(Messages.errorNoteContentRequired())).isVisible()
            assertThat(modal.validationError(Messages.errorNoteTimestampInvalid())).isVisible()
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, eventId).size shouldBe 0
        }

        "the timestamp of a note overrides its creation date in the order of the notes" {
            val eventId = createIncident()
            createIncidentNote(dslContext, IncidentType.HTTP, eventId, content = "Created first")

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)

            details.openNewNoteModal()
                .setContent("Backdated")
                // The seconds can be typed, even though the picker can't select them
                .setTimestamp("2020-01-01 10:00:15")
                .save()

            assertThat(details.notes).hasCount(2)
            details.noteContents shouldBe listOf("Backdated", "Created first")
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, eventId).first().timestamp
                .shouldNotBeNull()
                .second shouldBe TYPED_SECOND
        }

        "a note can be edited, which keeps its place on the timeline" {
            val eventId = createIncident()
            // The seconds are shown in the input, and kept as long as the timestamp isn't changed
            val timestamp = getCurrentTimestamp().minusHours(1).withSecond(NOTE_SECOND).truncatedTo(ChronoUnit.SECONDS)
            createIncidentNote(
                dslContext,
                IncidentType.HTTP,
                eventId,
                content = "Looking into it",
                timestamp = timestamp,
            )

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)

            val modal = details.openEditNoteModal("Looking into it")
            assertThat(modal.contentInput).hasValue("Looking into it")
            assertThat(modal.timestampInput).hasValue(Pattern.compile(".* \\d{2}:\\d{2}:$NOTE_SECOND"))
            modal.setContent("Found the root cause").selectStatus(InvestigationStatus.IDENTIFIED).save()

            val note = details.note("Found the root cause")
            assertThat(note).containsText(Messages.investigationStatusIdentified())
            assertThat(note.getByTestId("timeline-event-time")).hasText(
                timestamp.atZoneSameInstant(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)
            )
            assertThat(note.getByTestId("incident-note-edited")).hasText(Pattern.compile(".* [+-]\\d{4}"))
            assertThat(details.notes).hasCount(1)
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, eventId).single().timestamp
                .shouldNotBeNull()
                .toInstant() shouldBe timestamp.toInstant()
        }

        "a note can be deleted from the modal it's edited in" {
            val eventId = createIncident()
            createIncidentNote(dslContext, IncidentType.HTTP, eventId, content = "To be deleted")

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("http", eventId)

            details.openEditNoteModal("To be deleted").delete()

            assertThat(details.startedEvent).isVisible()
            assertThat(details.notes).hasCount(0)
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, eventId).size shouldBe 0
        }

        "the notes of an SSL incident can't be made public" {
            val event = createSSLEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository).id,
                status = SslStatus.INVALID,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )
            createIncidentNote(dslContext, IncidentType.SSL, event.id, content = "Renewing the certificate")

            val page = newPage()
            val details = IncidentDetailsPage(page)
            details.navigate("ssl", event.id)

            // SSL incidents are never shown on the status pages, so their notes are neither public nor private
            assertThat(details.note("Renewing the certificate")).isVisible()
            assertThat(details.note("Renewing the certificate").getByTestId("incident-note-visibility")).hasCount(0)
            val modal = details.openNewNoteModal()
            assertThat(modal.contentInput).isVisible()
            assertThat(modal.publicToggle).hasCount(0)
        }

        "a missing incident is not found" {
            val page = newPage()

            IncidentDetailsPage(page).navigate("http", MISSING_EVENT_ID)
                .shouldNotBeNull()
                .status() shouldBe NOT_FOUND
        }
    }

    private companion object {
        const val MISSING_EVENT_ID = 1234L
        const val NOT_FOUND = 404
        const val REFRESH_INTERVAL_MILLIS = 15_000L
        const val NO_REFRESH_PERIOD_MILLIS = 45_000L
        const val NO_REFRESH_GRACE_MILLIS = 500.0
        const val NOTE_SECOND = 42
        const val TYPED_SECOND = 15
        const val NOTE_OFFSET_MINUTES = 10L
        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z")
    }
}
