package com.kuvaszuptime.kuvasz.uitest.pages.icmp

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An ICMP monitor's detail page at `/icmp-monitors/{id}` (uptime block + latency and packet-loss charts).
class IcmpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#icmp-monitor-detail-heading .icon-tabler-tool")

    // ApexCharts containers; once rendered each holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#icmp-monitor-details-latency-chart svg")
    val packetLossChartSvg: Locator get() = page.locator("#icmp-monitor-details-packet-loss-chart svg")

    val configureButton: Locator get() = page.getByTestId("configure-button")

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
