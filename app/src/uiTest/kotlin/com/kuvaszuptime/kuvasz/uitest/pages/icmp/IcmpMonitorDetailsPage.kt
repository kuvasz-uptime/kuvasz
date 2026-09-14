package com.kuvaszuptime.kuvasz.uitest.pages.icmp

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An ICMP monitor's detail page at `/icmp-monitors/{id}` (uptime block + the merged latency/packet-loss chart).
class IcmpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#icmp-monitor-detail-heading .icon-tabler-tool")

    // ApexCharts container of latency and packet loss; once rendered it holds an `<svg>`.
    val metricsChartSvg: Locator get() = page.locator("#icmp-monitor-details-metrics-chart svg")

    // The markers of the incidents' starts and ends on the chart, and the tooltip shown when one of them is hovered
    val incidentMarkers: Locator get() =
        page.locator("#icmp-monitor-details-metrics-chart .apexcharts-point-annotation-marker")
    val incidentMarkerTooltip: Locator get() = page.locator(".apexcharts-annotation-tooltip")

    // The period selector of the metrics block, changing it refreshes the metrics without reloading the page
    val metricsPeriodSelector: Locator get() = page.getByTestId("metrics-period-selector")

    // The overlay covering the metrics while the ones of a newly selected period are loading
    val metricsLoadingOverlay: Locator get() = page.getByTestId("metrics-loading-overlay")

    // The switch that turns the periodic refresh of the metrics on and off
    val autoRefreshToggle: Locator get() = page.locator("input[name=autoRefreshToggle]")

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The badge in the header showing the monitor's category, if it has one
    val categoryBadge: Locator get() = page.getByTestId("monitor-category-badge")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    fun navigate(monitorId: Long) {
        page.navigate("/icmp-monitors/$monitorId")
    }

    fun openConfigureModal(): IcmpMonitorFormModal {
        configureButton.click()
        return IcmpMonitorFormModal(page)
    }
}
