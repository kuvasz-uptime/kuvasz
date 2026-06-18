package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// An HTTP monitor's detail page at `/http-monitors/{id}` (uptime/latency/SSL blocks + the latency chart).
class HttpMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val configureButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.configure())

    val uptimeSection: Locator get() = page.byRole(AriaRole.HEADING, Messages.uptimeBlockTitle())
    val latencySection: Locator get() = page.byRole(AriaRole.HEADING, Messages.latencyBlockTitle())
    val sslSection: Locator get() = page.byRole(AriaRole.HEADING, Messages.sslBlockTitle())

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
