package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.common.tooltipText
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/** The incidents on the public status pages: the accordions of the recent ones, the badges, the modal, the days. */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageIncidentsUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val statusPageCacheInvalidator: StatusPageCacheInvalidator,
) : UiTestSpec() {
    init {
        "an ongoing incident opens the active incidents, which show only its public notes" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Burning API")
            val incident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now().minusHours(1),
                endedAt = null,
                error = "Connection refused: internal-db.lan:5432",
            )
            createIncidentNote(
                dslContext,
                incidentType = IncidentType.HTTP,
                eventId = incident.id,
                content = "We are looking into the elevated error rates",
                public = true,
            )
            createIncidentNote(
                dslContext,
                incidentType = IncidentType.HTTP,
                eventId = incident.id,
                status = InvestigationStatus.IDENTIFIED,
                content = "Ops only: the primary DB node ran out of disk",
                public = false,
            )
            createStatusPage(
                dslContext,
                title = "Burning Status",
                slug = "burning-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("burning-status")

            assertThat(statusPage.activeIncidents.collapse).isVisible()
            assertThat(statusPage.activeIncidents.toggle).hasAttribute("aria-expanded", "true")
            // Without any recently resolved incident, there is no accordion for them at all
            assertThat(statusPage.resolvedIncidents.root).hasCount(0)
            val alert = statusPage.activeIncidents.alert(monitor.name)
            assertThat(alert).hasClass(Pattern.compile(".*alert-danger.*"))
            assertThat(alert.getByTestId("public-incident-started")).hasCount(1)
            assertThat(alert.getByTestId("public-incident-resolved")).hasCount(0)
            assertThat(alert.getByTestId("public-incident-note-content"))
                .hasText("We are looking into the elevated error rates")
            // Neither the private notes, nor the error of the check are ever sent to the visitors, not even hidden
            page.content() shouldNotContain "Ops only"
            page.content() shouldNotContain "internal-db.lan"
        }

        "the resolved incidents start closed, and the accordions keep the visitor's choices, unless an outage starts" {
            val recovered = createHttpMonitor(httpMonitorRepository, monitorName = "Recovered API")
            val steady = createHttpMonitor(httpMonitorRepository, monitorName = "Steady API")
            val now = OffsetDateTime.now()
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = recovered.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(2),
                endedAt = now.minusHours(1),
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = recovered.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            createStatusPage(
                dslContext,
                title = "Recovered Status",
                slug = "recovered-status",
                public = true,
                monitors = listOf(
                    MonitorID(MonitorType.HTTP_SSL, recovered.name),
                    MonitorID(MonitorType.HTTP_SSL, steady.name),
                ),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("recovered-status")
            val active = statusPage.activeIncidents
            val resolved = statusPage.resolvedIncidents

            assertThat(active.root).hasCount(0)
            assertThat(resolved.root).isVisible()
            assertThat(resolved.collapse).isHidden()
            assertThat(resolved.alert(recovered.name)).hasClass(Pattern.compile(".*alert-success.*"))

            // The visitor's choice survives the refreshes
            resolved.toggle.click()
            resolved.waitForRememberedChoice(isOpen = true)
            statusPage.refreshInPlace()
            assertThat(resolved.collapse).isVisible()

            // A new outage shows up open, above the resolved incidents, which keep the visitor's choice
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = steady.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            statusPageCacheInvalidator.invalidateAllCaches()
            statusPage.refreshInPlace()
            assertThat(active.collapse).isVisible()
            assertThat(active.toggle).hasAttribute("aria-expanded", "true")
            assertThat(active.alert(steady.name)).hasClass(Pattern.compile(".*alert-danger.*"))
            assertThat(resolved.collapse).isVisible()
            page.locator("[data-incidents-accordion]").evaluateAll(
                "collapses => collapses.map(collapse => collapse.dataset.incidentsAccordion)"
            ) shouldBe listOf("active", "resolved")

            // The active incidents can be closed too, which also survives the refreshes
            active.toggle.click()
            active.waitForRememberedChoice(isOpen = false)
            statusPage.refreshInPlace()
            assertThat(active.collapse).isHidden()
            assertThat(active.toggle).hasAttribute("aria-expanded", "false")
            assertThat(resolved.collapse).isVisible()
        }

        "the public timeline is the latest first, with the RESOLVED neighbours of the resolution shown as its part" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Healed API")
            val now = OffsetDateTime.now()
            val startedAt = now.minusHours(INCIDENT_HOURS_AGO)
            val endedAt = now.minusHours(1)
            val incident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = startedAt,
                endedAt = endedAt,
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = endedAt,
                endedAt = null,
            )
            fun note(content: String, status: InvestigationStatus, timestamp: OffsetDateTime) = createIncidentNote(
                dslContext,
                incidentType = IncidentType.HTTP,
                eventId = incident.id,
                status = status,
                content = content,
                timestamp = timestamp,
                public = true,
            )
            note("Investigating", InvestigationStatus.INVESTIGATING, startedAt.plusMinutes(NOTE_OFFSET_MINUTES))
            note("Identified", InvestigationStatus.IDENTIFIED, startedAt.plusMinutes(LATER_NOTE_OFFSET_MINUTES))
            note("Fixed it", InvestigationStatus.RESOLVED, endedAt.minusMinutes(1))
            note("Post-mortem", InvestigationStatus.RESOLVED, endedAt.plusMinutes(NOTE_OFFSET_MINUTES))
            createStatusPage(
                dslContext,
                title = "Healed Status",
                slug = "healed-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("healed-status")
            statusPage.resolvedIncidents.toggle.click()
            val alert = statusPage.resolvedIncidents.alert(monitor.name)
            assertThat(alert).isVisible()

            alert.locator(PUBLIC_TIMELINE_NODES).evaluateAll("nodes => nodes.map(node => node.dataset.testid)") shouldBe
                listOf(
                    "public-incident-resolved",
                    "public-incident-note",
                    "public-incident-note",
                    "public-incident-started",
                )
            // The resolution carries its neighbours in their order, then the standalone notes come
            alert.getByTestId("public-incident-note-content").allInnerTexts().map { it.trim() } shouldBe
                listOf("Fixed it", "Post-mortem", "Identified", "Investigating")
            // The times are shown in the system's time zone, with their offsets
            assertThat(alert.getByTestId("public-incident-resolved").getByTestId("timeline-event-time")).hasText(
                endedAt.atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z"))
            )
        }

        "an incident resolved more than a day ago is only counted on the monitor's badge, not in the accordion" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Calm API")
            val now = OffsetDateTime.now()
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusDays(2),
                endedAt = now.minusDays(2).plusMinutes(1),
            )
            createStatusPage(
                dslContext,
                title = "Calm Status",
                slug = "calm-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("calm-status")

            assertThat(statusPage.monitorIncidentsBadge(monitor.name)).hasText("1")
            assertThat(statusPage.activeIncidents.root).hasCount(0)
            assertThat(statusPage.resolvedIncidents.root).hasCount(0)
        }

        "the incidents badge opens the monitor's incidents, and the open modal follows the refreshes of the page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Flaky API")
            val quiet = createHttpMonitor(httpMonitorRepository, monitorName = "Quiet API")
            val now = OffsetDateTime.now()
            val olderIncident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusDays(2),
                endedAt = now.minusDays(2).plusHours(1),
            )
            createIncidentNote(
                dslContext,
                incidentType = IncidentType.HTTP,
                eventId = olderIncident.id,
                status = InvestigationStatus.RESOLVED,
                content = "Rolled back the release",
                public = true,
            )
            val latestIncident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusDays(1).minusHours(2),
                endedAt = now.minusDays(1).minusHours(1),
            )
            createStatusPage(
                dslContext,
                title = "Flaky Status",
                slug = "flaky-status",
                public = true,
                monitors = listOf(
                    MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    MonitorID(MonitorType.HTTP_SSL, quiet.name),
                ),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("flaky-status")

            // Only the monitor with incidents gets a badge, with the length of the history in its tooltip
            assertThat(statusPage.monitorIncidentsBadge(quiet.name)).hasCount(0)
            val badge = statusPage.monitorIncidentsBadge(monitor.name)
            assertThat(badge).hasText("2")
            badge.locator(".status").tooltipText() shouldBe Messages.statusPageIncidentsInTheLastDays("30")

            badge.click()
            val modal = statusPage.incidentsModal
            assertThat(modal).isVisible()
            assertThat(modal.locator(".modal-title")).hasText(Messages.statusPageIncidentsOfMonitor(monitor.name, "30"))
            assertThat(modal.getByTestId("public-incident")).hasCount(2)
            // The latest incident comes first
            assertThat(modal.getByTestId("public-incident").last()).containsText("Rolled back the release")

            createIncidentNote(
                dslContext,
                incidentType = IncidentType.HTTP,
                eventId = latestIncident.id,
                status = InvestigationStatus.RESOLVED,
                content = "Post-mortem published",
                public = true,
            )
            statusPageCacheInvalidator.invalidateAllCaches()
            statusPage.refreshInPlace()

            // The modal stays open and shows the refreshed incidents, without stacking another backdrop
            assertThat(modal).isVisible()
            assertThat(modal.getByTestId("public-incident").first()).containsText("Post-mortem published")
            assertThat(page.locator(".modal-backdrop")).hasCount(1)

            modal.locator(".btn-close").click()
            assertThat(modal).isHidden()
            assertThat(page.locator(".modal-backdrop")).hasCount(0)
        }

        "a day of the history spells out its downtime and its incidents in its tooltip" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Daily API")
            // The days of the history are counted in the system's time zone, the times are shown in it too
            val zone = ZoneId.systemDefault()
            val incidentDay = LocalDate.now(zone).minusDays(2)
            fun timeOf(day: LocalDate, hour: Int, minute: Long) =
                day.atTime(hour, 0).plusMinutes(minute).atZone(zone).toOffsetDateTime()
            // One incident from the evening before, through midnight, and another one later on the same day
            val overnightStart = timeOf(incidentDay.minusDays(1), OVERNIGHT_INCIDENT_HOUR, 0)
            val overnightEnd = timeOf(incidentDay, 0, OVERNIGHT_INCIDENT_MINUTES_AFTER_MIDNIGHT)
            val incidentStart = timeOf(incidentDay, INCIDENT_HOUR, 0)
            val incidentEnd = incidentStart.plusMinutes(INCIDENT_MINUTES)
            listOf(overnightStart to overnightEnd, incidentStart to incidentEnd).forEach { (startedAt, endedAt) ->
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = startedAt,
                    endedAt = endedAt,
                )
            }
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = incidentEnd,
                endedAt = null,
                updatedAt = OffsetDateTime.now(),
            )
            // Another monitor is down since the evening before
            val downMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "Nightly API")
            val yesterday = LocalDate.now(zone).minusDays(1)
            val ongoingStart = timeOf(yesterday, OVERNIGHT_INCIDENT_HOUR, 0)
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = downMonitor.id,
                status = UptimeStatus.DOWN,
                startedAt = ongoingStart,
                endedAt = null,
                updatedAt = OffsetDateTime.now(),
            )
            createStatusPage(
                dslContext,
                title = "Daily Status",
                slug = "daily-status",
                public = true,
                monitors = listOf(monitor, downMonitor).map { MonitorID(MonitorType.HTTP_SSL, it.name) },
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("daily-status")

            val blocks = statusPage.trackingBlocks(monitor.name)
            assertThat(blocks).hasCount(StatusPageDefaults.HISTORY_DAYS)
            assertThat(blocks.and(page.locator(".bg-danger"))).hasCount(2)
            fun tooltipsOf(blocks: Locator) = blocks.all().map { it.tooltipText().shouldNotBeNull() }
            val tooltips = tooltipsOf(blocks)
            // The date of the day is its bold heading
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            fun List<String>.tooltipOf(day: LocalDate) = single { it.contains(">${day.format(dateFormatter)}<") }
            // The spans are full timestamps, so their time zone is explicit
            val timestampFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z")
            fun OffsetDateTime.asTimestamp() = atZoneSameInstant(zone).format(timestampFormatter)
            fun LocalDate.midnight() = atStartOfDay(zone).toOffsetDateTime().asTimestamp()

            // The overnight incident is shown until the end of the day it started on...
            tooltips.tooltipOf(incidentDay.minusDays(1)) shouldContain
                "${overnightStart.asTimestamp()} - ${incidentDay.midnight()}"
            // ...and from the start of the next day, where the downtime of both incidents is summed up
            val dayTooltip = tooltips.tooltipOf(incidentDay)
            dayTooltip shouldContain "${incidentDay.midnight()} - ${overnightEnd.asTimestamp()}"
            dayTooltip shouldContain "${incidentStart.asTimestamp()} - ${incidentEnd.asTimestamp()}"
            val downtime = (OVERNIGHT_INCIDENT_MINUTES_AFTER_MIDNIGHT + INCIDENT_MINUTES) * SECONDS_IN_MINUTE
            dayTooltip shouldContain Messages.statusPageDayDowntime(downtime.formatAsInterval())
            // Today has data without any downtime
            tooltips.last() shouldContain Messages.statusPageDayNoDowntime()
            // The first days, before the monitor had any data
            tooltips.first() shouldContain Messages.noData()

            // An ongoing incident is only "ongoing" today, the days before it are covered until their end
            val downTooltips = tooltipsOf(statusPage.trackingBlocks(downMonitor.name))
            val today = yesterday.plusDays(1)
            downTooltips.tooltipOf(yesterday) shouldContain "${ongoingStart.asTimestamp()} - ${today.midnight()}"
            downTooltips.last() shouldContain "${today.midnight()} - ${Messages.statusPageIncidentOngoing()}"
        }
    }
}

private const val INCIDENT_HOUR = 10
private const val INCIDENT_MINUTES = 30L
private const val OVERNIGHT_INCIDENT_HOUR = 23
private const val OVERNIGHT_INCIDENT_MINUTES_AFTER_MIDNIGHT = 15L
private const val SECONDS_IN_MINUTE = 60L
private const val NOTE_OFFSET_MINUTES = 10L
private const val LATER_NOTE_OFFSET_MINUTES = 20L
private const val INCIDENT_HOURS_AGO = 3L
private const val PUBLIC_TIMELINE_NODES =
    "[data-testid=public-incident-started], [data-testid=public-incident-note], [data-testid=public-incident-resolved]"
