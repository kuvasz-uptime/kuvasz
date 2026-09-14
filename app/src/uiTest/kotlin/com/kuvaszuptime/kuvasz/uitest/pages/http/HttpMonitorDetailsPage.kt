package com.kuvaszuptime.kuvasz.uitest.pages.http

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An HTTP monitor's detail page at `/http-monitors/{id}` (uptime/latency/SSL blocks + the latency chart).
class HttpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The badge in the header showing the monitor's category, if it has one
    val categoryBadge: Locator get() = page.getByTestId("monitor-category-badge")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")
    val metricsSection: Locator get() = page.getByTestId("metrics-block-title")
    val sslSection: Locator get() = page.getByTestId("ssl-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#http-monitor-detail-heading .icon-tabler-tool")

    // The ApexCharts container; once rendered it holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#monitor-details-latency-chart svg")

    // The markers of the incidents' starts and ends on the chart, and the tooltip shown when one of them is hovered
    val incidentMarkers: Locator get() =
        page.locator("#monitor-details-latency-chart .apexcharts-point-annotation-marker")
    val incidentMarkerTooltip: Locator get() = page.locator(".apexcharts-annotation-tooltip")

    // The period selector of the metrics block, changing it refreshes the metrics without reloading the page
    val metricsPeriodSelector: Locator get() = page.getByTestId("metrics-period-selector")

    fun navigate(monitorId: Long) {
        page.navigate("/http-monitors/$monitorId")
    }

    fun openConfigureModal(): HttpMonitorFormModal {
        configureButton.click()
        return HttpMonitorFormModal(page)
    }
}
