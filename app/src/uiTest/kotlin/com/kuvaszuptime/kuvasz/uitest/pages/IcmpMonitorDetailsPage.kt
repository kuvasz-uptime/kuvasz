package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An ICMP monitor's detail page at `/icmp-monitors/{id}` (uptime block + latency and packet-loss charts).
class IcmpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.byRole(AriaRole.HEADING, Messages.uptimeBlockTitle())

    // ApexCharts containers; once rendered each holds an `<svg>`.
    val latencyChartSvg: Locator get() = page.locator("#icmp-monitor-details-latency-chart svg")
    val packetLossChartSvg: Locator get() = page.locator("#icmp-monitor-details-packet-loss-chart svg")

    val configureButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.configure())

    fun navigate(monitorId: Long) {
        page.navigate("/icmp-monitors/$monitorId")
    }

    fun openConfigureModal(): IcmpMonitorFormModal {
        configureButton.click()
        return IcmpMonitorFormModal(page)
    }
}
