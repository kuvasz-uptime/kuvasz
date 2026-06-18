package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A push monitor's detail page at `/push-monitors/{id}` (uptime block + incidents; no latency/SSL charts).
class PushMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.byRole(AriaRole.HEADING, Messages.uptimeBlockTitle())

    val content: Locator get() = page.locator("#push-monitor-details-content")

    val configureButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.configure())

    fun navigate(monitorId: Long) {
        page.navigate("/push-monitors/$monitorId")
    }

    fun openConfigureModal(): PushMonitorFormModal {
        configureButton.click()
        return PushMonitorFormModal(page)
    }
}
