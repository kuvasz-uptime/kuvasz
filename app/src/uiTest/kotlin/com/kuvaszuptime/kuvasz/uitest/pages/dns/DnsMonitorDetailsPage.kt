package com.kuvaszuptime.kuvasz.uitest.pages.dns

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A DNS monitor's detail page at `/dns-monitors/{id}` (uptime block + resolution-latency chart).
class DnsMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    // The maintenance indicator (a tool icon) rendered in the header while the monitor is under maintenance.
    val maintenanceIndicator: Locator get() = page.locator("#dns-monitor-detail-heading .icon-tabler-tool")

    // ApexCharts container; once rendered it holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#dns-monitor-details-latency-chart svg")

    // The auto-refreshing resolved-records snapshot block; empty until drift detection has recorded a snapshot.
    val snapshotSection: Locator get() = page.locator("#dns-monitor-details-snapshot")

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    fun navigate(monitorId: Long) {
        page.navigate("/dns-monitors/$monitorId")
    }

    fun openConfigureModal(): DnsMonitorFormModal {
        configureButton.click()
        return DnsMonitorFormModal(page)
    }
}
