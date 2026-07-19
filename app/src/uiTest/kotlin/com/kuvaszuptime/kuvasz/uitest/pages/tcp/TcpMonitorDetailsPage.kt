package com.kuvaszuptime.kuvasz.uitest.pages.tcp

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A TCP monitor's detail page at `/tcp-monitors/{id}` (uptime block + connect-latency chart).
class TcpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#tcp-monitor-detail-heading .icon-tabler-tool")

    // ApexCharts container; once rendered it holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#tcp-monitor-details-latency-chart svg")

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    fun navigate(monitorId: Long) {
        page.navigate("/tcp-monitors/$monitorId")
    }

    fun openConfigureModal(): TcpMonitorFormModal {
        configureButton.click()
        return TcpMonitorFormModal(page)
    }
}
