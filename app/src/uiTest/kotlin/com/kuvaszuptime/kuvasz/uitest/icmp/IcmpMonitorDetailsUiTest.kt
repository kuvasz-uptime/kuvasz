package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createIcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.concurrent.atomic.AtomicReference

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorDetailsUiTest(private val icmpMonitorRepository: IcmpMonitorRepository) : UiTestSpec() {
    init {
        "a seeded ICMP monitor's detail page renders the uptime block and the merged latency/packet-loss chart" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Detail Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            // ApexCharts renders an <svg> into the container even with no data (its no-data state).
            assertThat(details.metricsChartSvg).isVisible()
            assertThat(details.metricsPeriodSelector).hasValue(DEFAULT_PERIOD)
        }

        "the start and the end of an incident are marked on the chart, with its details in a tooltip" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Incident Marker Monitor")
            val now = getCurrentTimestamp()
            for (index in 0 until METRICS_LOG_COUNT) {
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitor.id,
                    createdAt = now.minusMinutes(index * METRICS_LOG_INTERVAL_MINUTES),
                )
            }
            createIcmpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusMinutes(INCIDENT_STARTED_MINUTES_AGO),
                endedAt = now.minusMinutes(INCIDENT_ENDED_MINUTES_AGO),
                error = "Request timed out",
            )

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.incidentMarkers).hasCount(2)
            details.incidentMarkers.first().hover()
            assertThat(details.incidentMarkerTooltip).containsText("Request timed out")
        }

        "changing the metrics period refreshes the metrics of the new period without reloading the page" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Period Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            assertThat(details.metricsChartSvg).isVisible()
            // A reload would drop this flag of the current document
            page.evaluate("window.notReloaded = true")

            val statsUrl = "/api/v2/icmp-monitors/${monitor.id}/stats?period=$SEVEN_DAYS_PERIOD"
            val incidentsUrl =
                "/api/v2/incidents?monitorId=${monitor.id}&period=$SEVEN_DAYS_PERIOD&includeResolved=true"
            page.waitForRequest({ it.url().endsWith(incidentsUrl) }) {
                page.waitForRequest({ it.url().endsWith(statsUrl) }) {
                    details.metricsPeriodSelector.selectOption(SEVEN_DAYS_PERIOD)
                }
            }

            page.evaluate("window.notReloaded === true") shouldBe true
        }

        "only an explicit change of the metrics period shows the loading overlay, the auto-refresh does not" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Period Loading Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            assertThat(details.metricsChartSvg).isVisible()

            // Turning the auto-refresh on polls the metrics right away, while the stale ones are kept on the screen
            val pendingAutoRefresh = holdStatsRequests(page, monitor.id, DEFAULT_PERIOD)
            details.autoRefreshToggle.check()
            page.waitForCondition { pendingAutoRefresh.get() != null }
            assertThat(details.metricsLoadingOverlay).isHidden()
            pendingAutoRefresh.get().shouldNotBeNull().resume()
            details.autoRefreshToggle.uncheck()

            val pendingPeriodChange = holdStatsRequests(page, monitor.id, SEVEN_DAYS_PERIOD)
            details.metricsPeriodSelector.selectOption(SEVEN_DAYS_PERIOD)
            page.waitForCondition { pendingPeriodChange.get() != null }
            assertThat(details.metricsLoadingOverlay).isVisible()

            pendingPeriodChange.get().shouldNotBeNull().resume()
            assertThat(details.metricsLoadingOverlay).isHidden()
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Detail Toggle Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }

        "a monitor under an active maintenance window shows the maintenance indicator in its heading" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "Maintained ICMP Detail Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "ICMP detail maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name)),
            )

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.maintenanceIndicator).isVisible()
        }
    }

    companion object {
        // Metrics logs recorded every 15 minutes in the last hour, with an incident in the middle of them
        private const val METRICS_LOG_COUNT = 5L
        private const val METRICS_LOG_INTERVAL_MINUTES = 15L
        private const val INCIDENT_STARTED_MINUTES_AGO = 40L
        private const val INCIDENT_ENDED_MINUTES_AGO = 20L
        private const val DEFAULT_PERIOD = "PT24H"
        private const val SEVEN_DAYS_PERIOD = "PT168H"

        /**
         * Holds back the stats requests of the monitor for the given [period]: the last one of them is kept in the
         * returned reference, and it's only answered once it's resumed.
         */
        private fun holdStatsRequests(page: Page, monitorId: Long, period: String): AtomicReference<Route?> {
            val pendingRoute = AtomicReference<Route?>()
            page.route({ it.endsWith("/api/v2/icmp-monitors/$monitorId/stats?period=$period") }) {
                pendingRoute.set(it)
            }
            return pendingRoute
        }
    }
}
