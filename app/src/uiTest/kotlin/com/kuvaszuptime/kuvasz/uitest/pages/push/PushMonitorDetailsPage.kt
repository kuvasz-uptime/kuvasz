package com.kuvaszuptime.kuvasz.uitest.pages.push

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A push monitor's detail page at `/push-monitors/{id}` (uptime block + incidents; no latency/SSL charts).
class PushMonitorDetailsPage(private val page: Page) {

    fun heading(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val uptimeSection: Locator get() = page.getByTestId("uptime-block-title")

    val content: Locator get() = page.locator("#push-monitor-details-content")

    val configureButton: Locator get() = page.getByTestId("configure-button")

    // The pause/resume control in the header: shows a pause icon while running, a play icon once paused.
    val toggleButton: Locator get() = page.getByTestId("toggle-monitor-button")
    val pauseControl: Locator get() = toggleButton.locator(".icon-tabler-player-pause")
    val resumeControl: Locator get() = toggleButton.locator(".icon-tabler-player-play")

    fun navigate(monitorId: Long) {
        page.navigate("/push-monitors/$monitorId")
    }

    fun openConfigureModal(): PushMonitorFormModal {
        configureButton.click()
        return PushMonitorFormModal(page)
    }
}
