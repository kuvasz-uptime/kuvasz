package com.kuvaszuptime.kuvasz.uitest.pages.docker

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A TCP monitor's detail page at `/docker-monitors/{id}` (uptime block + CPU and memory chart).
class DockerMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#docker-monitor-detail-heading .icon-tabler-tool")

    /**
     * The chart's own root `<svg>`. Pinned by the ApexCharts class rather than by the element name, because a
     * legend renders an `<svg>` marker per series, so a bare `svg` matches several.
     */
    val metricsChartSvg: Locator get() = chart.locator(".apexcharts-svg")

    private val chart: Locator get() = page.locator("#docker-monitor-details-metrics-chart")

    /** A series' entry in the chart legend, which is also the control that toggles it. */
    fun chartLegendItem(seriesName: String): Locator =
        chart.locator(".apexcharts-legend-series").filter(Locator.FilterOptions().setHasText(seriesName))

    /** The same entry, but only while the series is collapsed. */
    fun collapsedChartLegendItem(seriesName: String): Locator =
        chart.locator(".apexcharts-legend-series.apexcharts-inactive-legend")
            .filter(Locator.FilterOptions().setHasText(seriesName))

    // The period selector of the metrics block, changing it refreshes the metrics without reloading the page
    // The heading badge flagging a monitor whose Docker host is not in the config anymore.
    val danglingHostBadge: Locator get() = page.getByTestId("docker-host-not-configured-badge")

    val imageBadge: Locator get() = page.getByTestId("docker-image-badge")

    // The tooltip of the image badge, which is rendered as HTML. Bootstrap moves the rendered `title` into
    // `data-bs-original-title` when it takes the element over.
    val imageBadgeTooltip: String?
        get() = imageBadge.locator(".status").let { badge ->
            badge.getAttribute("data-bs-original-title") ?: badge.getAttribute("title")
        }

    val metricsPeriodSelector: Locator get() = page.getByTestId("metrics-period-selector")

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The heading refreshes itself in place, so its id must stay unique on the page
    val headingElements: Locator get() = page.locator("#docker-monitor-detail-heading")

    // Swapped out-of-band by every heading refresh
    val uptimeSummary: Locator get() = page.locator("#docker-monitor-details-uptime-summary")

    // The badge in the header showing the monitor's category, if it has one
    val categoryBadge: Locator get() = page.getByTestId("monitor-category-badge")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    fun navigate(monitorId: Long) {
        page.navigate("/docker-monitors/$monitorId")
    }

    fun openConfigureModal(): DockerMonitorFormModal {
        configureButton.click()
        return DockerMonitorFormModal(page)
    }
}
