package com.kuvaszuptime.kuvasz.uitest.incident

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.incident.IncidentListPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
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
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            return httpIncident.id
        }

        "the incidents can be filtered by their type, and the period filter keeps the type" {
            createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate()
            assertThat(incidents.rows).hasCount(2)

            incidents.filterByType("docker")
            assertThat(page).hasURL(Pattern.compile(".*/incidents\\?type=docker$"))
            assertThat(incidents.rows).hasCount(1)
            assertThat(incidents.rows).containsText("Docker Incident Monitor")
            assertThat(incidents.count).hasText(Messages.incidentListCount(1))

            incidents.filterByPeriod("PT720H")
            assertThat(page).hasURL(Pattern.compile(".*/incidents\\?type=docker&period=PT720H$"))
            assertThat(incidents.typeFilter).hasValue("docker")
            assertThat(incidents.periodFilter).hasValue("PT720H")
            assertThat(incidents.rows).hasCount(1)

            // Going back to every type keeps the period only
            incidents.filterByType("")
            assertThat(page).hasURL(Pattern.compile(".*/incidents\\?period=PT720H$"))
            assertThat(incidents.rows).hasCount(2)
        }

        "the refresh button reloads the list in place, keeping the filters" {
            createIncidents()

            val page = newPage()
            val incidents = IncidentListPage(page)
            incidents.navigate("?type=http")
            assertThat(incidents.rows).hasCount(1)
            assertThat(incidents.count).hasText(Messages.incidentListCount(1))

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
            assertThat(incidents.count).hasText(Messages.incidentListCount(2))
            assertThat(page).hasURL(Pattern.compile(".*/incidents\\?type=http$"))
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

        "the filters are stacked on top of each other on small screens" {
            createIncidents()

            val page = newPage()
            page.setViewportSize(MOBILE_WIDTH, MOBILE_HEIGHT)
            val incidents = IncidentListPage(page)
            incidents.navigate()
            assertThat(incidents.typeFilter).isVisible()

            val periodBox = incidents.periodFilter.boundingBox().shouldNotBeNull()
            val typeBox = incidents.typeFilter.boundingBox().shouldNotBeNull()
            typeBox.y shouldBeGreaterThan periodBox.y + periodBox.height
        }
    }

    private companion object {
        const val MOBILE_WIDTH = 375
        const val MOBILE_HEIGHT = 812
    }
}
