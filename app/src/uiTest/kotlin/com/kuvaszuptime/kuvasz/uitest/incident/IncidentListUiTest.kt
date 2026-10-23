package com.kuvaszuptime.kuvasz.uitest.incident

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.common.PagedListView
import com.kuvaszuptime.kuvasz.uitest.pages.incident.IncidentListPage
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.regex.Pattern

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IncidentListUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : UiTestSpec() {
    init {
        fun createIncidents(): Long {
            val now = getCurrentTimestamp()
            val httpIncident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP Incident Monitor").id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            createDockerUptimeEventRecord(
                dslContext,
                monitorId = createDockerMonitor(dockerMonitorRepository, monitorName = "Docker Incident Monitor").id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(2),
                endedAt = null,
            )
            return httpIncident.id
        }

        fun createResolvedHttpIncident(monitorName: String, startedHoursAgo: Long) =
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository, monitorName = monitorName).id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusHours(startedHoursAgo),
                endedAt = getCurrentTimestamp().minusHours(startedHoursAgo - 1),
            )

        "the list covers the default period, until another one is picked" {
            createIncidents()
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "Old Incident Monitor").id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(OLD_INCIDENT_AGE_DAYS),
                endedAt = getCurrentTimestamp().minusDays(OLD_INCIDENT_AGE_DAYS - 1),
            )

            val page = newPage()
            val incidents = IncidentListPage(page)
            val filters = incidents.pagedList
            incidents.navigate()

            assertThat(incidents.title).hasText(Messages.incidents())
            assertThat(incidents.rows).hasCount(2)
            filters.chosenLabel("period") shouldBe
                ListDefaults.INCIDENT_PERIOD.formatAsSimpleInterval()

            filters.openFilters()
            filters.choose("period", UIDefaults.LAST_30_DAYS.formatAsSimpleInterval())
            incidents.expectQuery("period=PT720H")
            assertThat(incidents.rows.filter(Locator.FilterOptions().setHasText("Old Incident Monitor"))).hasCount(1)
        }

        "the incidents can be filtered by their type, and the period filter keeps the type" {
            createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            val filters = incidents.pagedList
            incidents.navigate()
            assertThat(incidents.rows).hasCount(2)

            filters.openFilters()
            filters.choose("type", "Docker")
            incidents.expectQuery("type=docker")
            assertThat(incidents.rows).hasCount(1)
            assertThat(incidents.rows).containsText("Docker Incident Monitor")
            assertThat(filters.paginationSummary).hasText(Messages.listPaginationSummary(1, 1, 1))

            filters.choose("period", UIDefaults.LAST_30_DAYS.formatAsSimpleInterval())
            incidents.expectQuery("type=docker&period=PT720H")
            assertThat(incidents.rows).hasCount(1)

            // Going back to every type keeps the period only
            filters.choose("type", Messages.allIncidentTypes())
            incidents.expectQuery("period=PT720H")
            assertThat(incidents.rows).hasCount(2)
        }

        "a link to the list with a type in any case opens it filtered by the type" {
            createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate("?type=DOCKER&period=P30D")

            assertThat(incidents.rows).hasCount(1)
            // ...and the URL is put into the form the list itself uses
            incidents.expectQuery("type=docker&period=PT720H")
            incidents.pagedList.chosenLabel("type") shouldBe "Docker"
        }

        "the incidents can be searched by the name of their monitor, and the resolved ones can be left out" {
            createIncidents()
            createResolvedHttpIncident("Resolved HTTP Monitor", startedHoursAgo = 3)

            val page = newPage()
            val incidents = IncidentListPage(page)
            val filters = incidents.pagedList
            incidents.navigate()
            assertThat(incidents.rows).hasCount(INCIDENTS_WITH_A_RESOLVED_ONE)

            filters.openFilters()
            filters.searchByName("http")
            incidents.expectQuery("name=http")
            assertThat(incidents.rows).hasCount(2)

            filters.choose("includeResolved", Messages.no())
            incidents.expectQuery("name=http&includeResolved=false")
            assertThat(incidents.rows).hasCount(1)
            assertThat(incidents.rows).containsText("HTTP Incident Monitor")
            assertThat(filters.activeFilterCount).hasText("2")

            // Every other filter is cleared to its default, the period included
            filters.clearFiltersButton.click()
            incidents.expectQuery()
            assertThat(incidents.rows).hasCount(INCIDENTS_WITH_A_RESOLVED_ONE)
        }

        "the incidents of a single monitor can be listed by its ID" {
            createIncidents()
            val monitorId = httpMonitorRepository.findByName("HTTP Incident Monitor").shouldNotBeNull().id
            createResolvedHttpIncident("Other HTTP Monitor", startedHoursAgo = 3)

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate("?type=http&monitorId=$monitorId")

            assertThat(incidents.rows).hasCount(1)
            assertThat(incidents.rows).containsText("HTTP Incident Monitor")
            incidents.pagedList.openFilters()
            assertThat(incidents.pagedList.filter("monitorId")).hasValue("$monitorId")
        }

        "the incidents are the latest started first, and they can be sorted by clicking the headers" {
            createIncidents()
            createResolvedHttpIncident("A Resolved Monitor", startedHoursAgo = 3)

            val page = newPage()
            val incidents = IncidentListPage(page)
            val sorting = incidents.pagedList
            val monitorNames = incidents.rows.locator("td:nth-child(2)")
            incidents.navigate()
            assertThat(monitorNames).hasText(
                arrayOf("HTTP Incident Monitor", "Docker Incident Monitor", "A Resolved Monitor")
            )
            sorting.expectSortState("STARTED_AT", "descending")

            sorting.sortHeader("MONITOR_NAME").click()
            incidents.expectQuery("sortedBy=MONITOR_NAME")
            assertThat(monitorNames).hasText(
                arrayOf("A Resolved Monitor", "Docker Incident Monitor", "HTTP Incident Monitor")
            )

            // A newly picked field is sorted ascending, so the oldest incident comes first
            sorting.sortHeader("STARTED_AT").click()
            incidents.expectQuery("sortedBy=STARTED_AT")
            assertThat(monitorNames).hasText(
                arrayOf("A Resolved Monitor", "Docker Incident Monitor", "HTTP Incident Monitor")
            )
        }

        "the incidents are paged" {
            val monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "Flapping Monitor").id
            for (hoursAgo in 1L..INCIDENT_COUNT) {
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitorId,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(hoursAgo),
                    endedAt = getCurrentTimestamp().minusHours(hoursAgo).plusMinutes(1),
                )
            }

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate()
            assertThat(incidents.rows).hasCount(PageRequest.DEFAULT_SIZE)

            incidents.pagedList.pageLink(2).click()
            incidents.expectQuery("page=2")
            assertThat(incidents.rows).hasCount(INCIDENT_COUNT.toInt() - PageRequest.DEFAULT_SIZE)
        }

        "the refresh button reloads the list in place, keeping the filters" {
            createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate("?type=http")
            assertThat(incidents.rows).hasCount(1)

            createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "New HTTP Incident Monitor").id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )
            incidents.refreshButton.click()

            assertThat(incidents.rows).hasCount(2)
            val newRow = incidents.rows.filter(Locator.FilterOptions().setHasText("New HTTP Incident Monitor"))
            assertThat(newRow).hasCount(1)
            assertThat(incidents.pagedList.paginationSummary).hasText(Messages.listPaginationSummary(1, 2, 2))
            incidents.expectQuery("type=http")
        }

        "an incident of the list leads to its details page" {
            val eventId = createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate()

            incidents.detailsButtonOf("HTTP Incident Monitor").click()
            assertThat(page).hasURL(Pattern.compile(".*/incidents/http/$eventId$"))
        }

        "an incident in the incident list of a monitor leads to its details page" {
            val eventId = createIncidents()
            val monitorId = httpMonitorRepository.findByName("HTTP Incident Monitor").shouldNotBeNull().id

            val page = newPage()
            page.navigate("/http-monitors/$monitorId")
            page.locator("#http-monitor-details-incidents").getByTestId("incident-details-button").click()

            assertThat(page).hasURL(Pattern.compile(".*/incidents/http/$eventId$"))
        }

        "the incident list of a monitor is paged in place, and links to the incidents page filtered by the monitor" {
            val monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "Flapping Monitor").id
            for (hoursAgo in 1L..INCIDENT_COUNT) {
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitorId,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(hoursAgo),
                    endedAt = getCurrentTimestamp().minusHours(hoursAgo).plusMinutes(1),
                )
            }

            val page = newPage()
            page.navigate("/http-monitors/$monitorId")
            val block = page.locator("#http-monitor-details-incidents")
            val rows = block.locator("tbody tr")
            assertThat(rows).hasCount(PageRequest.DEFAULT_SIZE)

            // The pages link to the incidents page, but they are loaded in place, without touching the URL
            val secondPage = PagedListView(page, "http-monitor-details-incidents").pageLink(2)
            assertThat(secondPage).hasAttribute("href", "/incidents?type=http&monitorId=$monitorId&page=2")
            secondPage.click()
            assertThat(rows).hasCount(INCIDENT_COUNT.toInt() - PageRequest.DEFAULT_SIZE)
            assertThat(page).hasURL(Pattern.compile(".*/http-monitors/$monitorId$"))

            page.getByTestId("all-incidents-link").first().click()
            assertThat(page).hasURL(Pattern.compile(".*/incidents\\?type=http&monitorId=$monitorId$"))
            assertThat(IncidentListPage(page).rows).hasCount(PageRequest.DEFAULT_SIZE)
        }

        "an incident on the dashboard leads to its details page" {
            val eventId = createIncidents()

            val page = newPage()
            page.navigate("/")
            page.getByTestId("dashboard-incident")
                .filter(Locator.FilterOptions().setHasText("HTTP Incident Monitor"))
                .locator("a")
                .click()

            assertThat(page).hasURL(Pattern.compile(".*/incidents/http/$eventId$"))
        }

        "the filters are stacked on top of each other on small screens" {
            createIncidents()

            val page = newPage()
            page.setViewportSize(MOBILE_WIDTH, MOBILE_HEIGHT)
            val incidents = IncidentListPage(page)
            incidents.navigate()
            incidents.pagedList.openFilters()
            assertThat(incidents.pagedList.filter("period")).isVisible()

            val typeBox = incidents.pagedList.filter("type").boundingBox().shouldNotBeNull()
            val periodBox = incidents.pagedList.filter("period").boundingBox().shouldNotBeNull()
            periodBox.y shouldBeGreaterThan typeBox.y + typeBox.height
        }
    }

    private companion object {
        const val MOBILE_WIDTH = 375
        const val MOBILE_HEIGHT = 812
        const val OLD_INCIDENT_AGE_DAYS = 10L
        const val INCIDENT_COUNT = PageRequest.DEFAULT_SIZE + 2L

        // The ones of createIncidents(), and a resolved one
        const val INCIDENTS_WITH_A_RESOLVED_ONE = 3
    }
}
