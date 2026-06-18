package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The authenticated dashboard at `/`. Its HTTP/Push/ICMP stat regions are filled in via HTMX after load.
class DashboardPage(private val page: Page) {

    val heading: Locator get() = page.byRole(AriaRole.HEADING, Messages.monitoring())

    val httpStatsRegion: Locator get() = page.locator("#http-monitoring-dashboard")

    // Only appears once the HTMX stat fragment has been swapped in.
    val httpSectionHeader: Locator get() = httpStatsRegion.byRole(AriaRole.HEADING, "HTTP")

    val httpStatCards: Locator get() = httpStatsRegion.locator(".card")

    fun navigate() {
        page.navigate("/")
    }
}
