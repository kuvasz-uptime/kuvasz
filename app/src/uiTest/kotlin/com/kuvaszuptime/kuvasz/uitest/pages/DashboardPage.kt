package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The authenticated dashboard at `/`. Its per-type stat regions and its empty state are filled in via HTMX after load.
class DashboardPage(private val page: Page) {

    val heading: Locator get() = page.getByTestId("dashboard-title")

    // The placeholder shown as long as there isn't a single monitor of any type
    val emptyState: Locator get() = page.getByTestId("empty-state")

    val httpStatsRegion: Locator get() = statsRegionOf("http")

    // Only appears once the HTMX stat fragment has been swapped in
    val httpSectionHeader: Locator get() = sectionHeaderOf("http", "HTTP")

    val httpStatCards: Locator get() = httpStatsRegion.getByTestId("stat-card")

    // SSL is checked by the HTTP monitors, so its section is rendered by the HTTP fragment
    val sslSectionHeader: Locator get() = sectionHeaderOf("http", "SSL")

    // The table of the monitors currently having issues, rendered only when a section has something to call out
    fun issuesBlockOf(type: String): Locator = statsRegionOf(type).getByTestId("uptime-issues-block")

    fun navigate() {
        page.navigate("/")
    }

    /** The region a monitor type's section is swapped into. It stays empty while the type has no monitors at all. */
    fun statsRegionOf(type: String): Locator = page.locator("#$type-monitoring-dashboard")

    fun sectionHeaderOf(type: String, title: String): Locator =
        statsRegionOf(type).byRole(AriaRole.HEADING, title)

    fun refresh() {
        page.getByTestId("dashboard-refresh-button").click()
    }
}
