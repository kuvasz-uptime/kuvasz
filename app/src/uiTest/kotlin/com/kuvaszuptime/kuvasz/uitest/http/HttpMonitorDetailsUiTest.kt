package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorDetailsUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a seeded monitor's detail page renders the uptime, metrics and SSL blocks and the latency chart" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Detail Page Monitor")

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            assertThat(details.metricsSection).isVisible()
            assertThat(details.sslSection).isVisible()
            // ApexCharts renders an <svg> even with no data (its no-data state).
            assertThat(details.latencyChartSvg).isVisible()
            assertThat(details.metricsPeriodSelector).hasValue("PT24H")
        }

        "an ongoing incident is marked on the latency chart, even without any latency logs in the period" {
            // An HTTP monitor only logs the latency of its successful checks, so there are no logs while it's down
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Down Detail Monitor")
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusHours(INCIDENT_STARTED_HOURS_AGO),
                endedAt = null,
                error = "Connection refused",
            )

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.incidentMarkers).hasCount(1)
            details.incidentMarkers.first().hover()
            assertThat(details.incidentMarkerTooltip).containsText("Connection refused")
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Detail Toggle Monitor")

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }

        "a monitor under an active maintenance window shows the maintenance indicator in its heading" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Maintained Detail Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "HTTP detail maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.maintenanceIndicator).isVisible()
        }
    }

    companion object {
        private const val INCIDENT_STARTED_HOURS_AGO = 3L
    }
}
