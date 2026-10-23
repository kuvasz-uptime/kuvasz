package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
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
import com.kuvaszuptime.kuvasz.uitest.lightThemeToggle
import com.kuvaszuptime.kuvasz.uitest.pages.common.METRICS_RANGE_VALUE_PATTERN
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.Mouse
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

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
            assertThat(details.metricsRangeFrom).isEmpty()
            assertThat(details.metricsRangeTo).isEmpty()
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

        "the chart takes the colors of the theme again, when it is switched between dark and light" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Theme Monitor")
            val now = getCurrentTimestamp()
            for (index in 0 until METRICS_LOG_COUNT) {
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitor.id,
                    createdAt = now.minusMinutes(index * METRICS_LOG_INTERVAL_MINUTES),
                )
            }

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            // The inverted accent is the one that differs between the dark and the light mode
            page.evaluate("localStorage.setItem('kuvasz-theme-primary', 'inverted')")
            page.reload()
            assertThat(details.latencyLine).hasAttribute("stroke", INVERTED_ACCENT_IN_DARK_MODE)

            page.lightThemeToggle.click()
            assertThat(details.latencyLine).hasAttribute("stroke", INVERTED_ACCENT_IN_LIGHT_MODE)
        }

        "changing the metrics period or range refreshes the metrics without reloading the page" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Period Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            assertThat(details.metricsChartSvg).isVisible()
            // A reload would drop this flag of the current document
            page.evaluate("window.notReloaded = true")
            val chartUrl = "/api/internal/monitor-charts/icmp/${monitor.id}"

            page.waitForRequest({ it.url().endsWith("$chartUrl?period=$SEVEN_DAYS_PERIOD") }) {
                details.metricsPeriodSelector.selectOption(SEVEN_DAYS_PERIOD)
            }

            // A custom range is fetched once both of its ends are picked, and it clears the period selector
            val from = queryValueOf(page, "2024-01-05T10:30")
            val to = queryValueOf(page, "2024-01-06T08:00:15")
            page.waitForRequest({ it.url().endsWith("$chartUrl?from=$from&to=$to") }) {
                details.typeRangeEnd(details.metricsRangeFrom, "2024-01-05 10:30")
                details.typeRangeEnd(details.metricsRangeTo, "2024-01-06 08:00:15")
            }
            assertThat(details.metricsPeriodSelector).hasValue("")

            // Picking a period again clears the range
            page.waitForRequest({ it.url().endsWith("$chartUrl?period=$DEFAULT_PERIOD") }) {
                details.metricsPeriodSelector.selectOption(DEFAULT_PERIOD)
            }
            assertThat(details.metricsRangeFrom).isEmpty()
            assertThat(details.metricsRangeTo).isEmpty()

            page.evaluate("window.notReloaded === true") shouldBe true
        }

        "on a phone, the auto-refresh toggle goes into a row of its own, below the pickers of the range" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Phone Monitor")

            val page = newPage()
            page.setViewportSize(PHONE_WIDTH, PHONE_HEIGHT)
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            val to = details.metricsRangeTo.boundingBox().shouldNotBeNull()
            val toggle = details.autoRefreshToggle.boundingBox().shouldNotBeNull()
            toggle.y shouldBeGreaterThan to.y + to.height
        }

        "an invalid custom range is marked on the pickers, until it's corrected" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Invalid Period Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            assertThat(details.metricsChartSvg).isVisible()

            // The end of the range is required
            details.typeRangeEnd(details.metricsRangeFrom, "2024-01-05 10:30")
            assertThat(details.metricsRangeTo).hasClass(IS_INVALID)
            assertThat(details.metricsRangeFrom).not().hasClass(IS_INVALID)

            details.typeRangeEnd(details.metricsRangeTo, "2024-01-0")
            assertThat(details.metricsRangeTo).hasClass(IS_INVALID)

            // The start has to be earlier than the end
            details.typeRangeEnd(details.metricsRangeTo, "2000-01-01 00:00")
            assertThat(details.metricsRangeFrom).hasClass(IS_INVALID)
            assertThat(details.metricsRangeTo).hasClass(IS_INVALID)
            // The period is still shown in the meantime
            assertThat(details.metricsPeriodSelector).hasValue(DEFAULT_PERIOD)

            // The end of a range picked with a minute precision is the end of that minute
            val from = queryValueOf(page, "2024-01-05T10:30")
            val to = queryValueOf(page, "2024-01-06T08:00:59.999")
            val query = "from=$from&to=$to"
            page.waitForRequest({ it.url().endsWith("/api/internal/monitor-charts/icmp/${monitor.id}?$query") }) {
                details.typeRangeEnd(details.metricsRangeTo, "2024-01-06 08:00")
            }
            assertThat(details.metricsRangeFrom).not().hasClass(IS_INVALID)
            assertThat(details.metricsRangeTo).not().hasClass(IS_INVALID)
        }

        "the time picker of a range picker can be dragged over several steps in one go" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Time Drag Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            details.metricsRangeFrom.click()
            val hourSlider = details.openCalendar.locator("[data-vc-time-range='hour'] input")
            val hourInput = details.openCalendar.locator("[data-vc-time-input='hour'] input")
            hourSlider.scrollIntoViewIfNeeded()
            val slider = hourSlider.boundingBox().shouldNotBeNull()
            val middle = slider.y + slider.height / 2

            page.mouse().move(slider.x + 2, middle)
            page.mouse().down()
            page.mouse().move(
                slider.x + slider.width * HOUR_DRAG_END,
                middle,
                Mouse.MoveOptions().setSteps(MOUSE_MOVE_STEPS),
            )
            page.mouse().up()

            // Dragged from midnight to the middle of the day, not just to the next hour
            hourInput.inputValue().toInt() shouldBeGreaterThan DRAGGED_HOURS_AT_LEAST
        }

        "the pickers of the metrics period only offer the days that keep its start before its end" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Period Days Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            details.typeRangeEnd(details.metricsRangeFrom, "2024-01-10 10:00")
            details.typeRangeEnd(details.metricsRangeTo, "2024-01-20 10:00")

            details.metricsRangeFrom.click()
            assertThat(details.openCalendar.locator("[data-vc-date='2024-01-21']")).hasAttribute(DAY_DISABLED, "")
            assertThat(details.openCalendar.locator("[data-vc-date='2024-01-20']")).not().hasAttribute(DAY_DISABLED, "")
            page.keyboard().press("Escape")

            details.metricsRangeTo.click()
            assertThat(details.openCalendar.locator("[data-vc-date='2024-01-09']")).hasAttribute(DAY_DISABLED, "")
            assertThat(details.openCalendar.locator("[data-vc-date='2024-01-10']")).not().hasAttribute(DAY_DISABLED, "")
        }

        "selecting an interval on the chart narrows the metrics period down to it" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Zoom Monitor")
            val now = getCurrentTimestamp()
            for (index in 0 until METRICS_LOG_COUNT) {
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitor.id,
                    createdAt = now.minusMinutes(index * METRICS_LOG_INTERVAL_MINUTES),
                )
            }

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            // The latency is the same at every check, so its line is flat, which Playwright never sees as visible
            assertThat(details.latencyLine).isAttached()
            // The mouse moves within the viewport, which the chart is below of
            details.metricsChartPlotArea.scrollIntoViewIfNeeded()
            val plotArea = details.metricsChartPlotArea.boundingBox().shouldNotBeNull()
            val middle = plotArea.y + plotArea.height / 2

            page.waitForRequest({ "from=" in it.url() && "&to=" in it.url() }) {
                page.mouse().move(plotArea.x + plotArea.width * ZOOM_START, middle)
                page.mouse().down()
                page.mouse().move(
                    plotArea.x + plotArea.width * ZOOM_END,
                    middle,
                    Mouse.MoveOptions().setSteps(MOUSE_MOVE_STEPS),
                )
                page.mouse().up()
            }

            // The selected interval is shown in the pickers instead of the period
            assertThat(details.metricsRangeFrom).hasValue(METRICS_RANGE_VALUE_PATTERN)
            assertThat(details.metricsRangeTo).hasValue(METRICS_RANGE_VALUE_PATTERN)
            assertThat(details.metricsPeriodSelector).hasValue("")
        }

        "only an explicit change of the metrics period shows the loading overlay, the auto-refresh does not" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Period Loading Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)
            assertThat(details.metricsChartSvg).isVisible()

            // Turning the auto-refresh on polls the metrics right away, while the stale ones are kept on the screen
            val pendingAutoRefresh = holdChartRequests(page, monitor.id) { it == "period=$DEFAULT_PERIOD" }
            details.autoRefreshToggle.check()
            page.waitForCondition { pendingAutoRefresh.get() != null }
            assertThat(details.metricsLoadingOverlay).isHidden()
            pendingAutoRefresh.get().shouldNotBeNull().resume()
            details.autoRefreshToggle.uncheck()

            val pendingPeriodChange = holdChartRequests(page, monitor.id) { it == "period=$SEVEN_DAYS_PERIOD" }
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
            // The paused state arrives via the heading's refresh, which must not nest the heading
            assertThat(details.uptimeSummary).containsText(Messages.monitorIsPaused())
            assertThat(details.headingElements).hasCount(1)

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
        private val IS_INVALID: Pattern = Pattern.compile(".*is-invalid.*")
        private const val DAY_DISABLED = "data-vc-date-disabled"
        // The part of the plot area selected on the chart, in fractions of its width
        private const val ZOOM_START = 0.3
        private const val ZOOM_END = 0.7
        private const val MOUSE_MOVE_STEPS = 10
        // The share of the width of the hour slider dragged over, and the hour it surely gets past then
        private const val HOUR_DRAG_END = 0.6
        private const val DRAGGED_HOURS_AT_LEAST = 6
        private const val PHONE_WIDTH = 375
        private const val PHONE_HEIGHT = 812
        private const val INVERTED_ACCENT_IN_DARK_MODE = "#fafafa"
        private const val INVERTED_ACCENT_IN_LIGHT_MODE = "#1f2937"

        /**
         * Holds back the chart requests of the monitor whose query matches [isHeld]: the last one of them is kept in
         * the returned reference, and it's only answered once it's resumed.
         */
        private fun holdChartRequests(
            page: Page,
            monitorId: Long,
            isHeld: (String) -> Boolean,
        ): AtomicReference<Route?> {
            val pendingRoute = AtomicReference<Route?>()
            val chartUrl = "/api/internal/monitor-charts/icmp/$monitorId?"
            page.route({ chartUrl in it && isHeld(it.substringAfter(chartUrl)) }) {
                pendingRoute.set(it)
            }
            return pendingRoute
        }

        // A local date and time of the browser, as the pickers send it in the query of the chart requests
        private fun queryValueOf(page: Page, localDateTime: String): String =
            page.evaluate("encodeURIComponent(new Date('$localDateTime').toISOString())") as String
    }
}
