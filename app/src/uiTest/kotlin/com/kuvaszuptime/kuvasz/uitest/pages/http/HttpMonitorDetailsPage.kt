package com.kuvaszuptime.kuvasz.uitest.pages.http

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An HTTP monitor's detail page at `/http-monitors/{id}` (uptime/latency/SSL blocks + the latency chart).
class HttpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")
    val latencySection: Locator get() = page.getByTestId("latency-block-title")
    val sslSection: Locator get() = page.getByTestId("ssl-block-title")

    // The ApexCharts container; once rendered it holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#monitor-details-latency-chart svg")

    fun navigate(monitorId: Long) {
        page.navigate("/http-monitors/$monitorId")
    }

    fun openConfigureModal(): HttpMonitorFormModal {
        configureButton.click()
        return HttpMonitorFormModal(page)
    }
}
