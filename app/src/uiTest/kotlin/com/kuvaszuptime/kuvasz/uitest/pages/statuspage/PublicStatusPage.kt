package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.kuvaszuptime.kuvasz.uitest.pages.common.tooltipText
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Request
import com.microsoft.playwright.assertions.PlaywrightAssertions
import com.microsoft.playwright.options.AriaRole
import java.util.regex.Pattern

// A public status page at `/status/{slug}` — reachable without authentication when the page is marked public.
class PublicStatusPage(private val page: Page) {

    fun navigate(slug: String) {
        page.navigate("/status/$slug")
    }

    // The part of the page that refreshes itself every minute, through the page's own URL
    val content: Locator = page.locator("#status-page-content")

    /** Refreshes the page in place right away, instead of waiting for its next poll, and waits for the response. */
    fun refreshInPlace(): Request {
        val response = page.waitForResponse({ it.request().headerValue("hx-request") == "true" }) {
            content.dispatchEvent("refresh-status-page")
        }
        // The swap happens after the response has arrived, and the settling of the swapped content right after it
        page.waitForFunction(
            "() => !document.querySelector('#status-page-content.htmx-request, #status-page-content.htmx-settling')"
        )
        return response.request()
    }

    fun monitorCard(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val monitorCards: Locator get() = page.getByTestId("status-monitor-card")

    val monitorNames: List<String> get() = monitorCards.locator("h3").allInnerTexts().map { it.trim() }

    fun monitorCardBody(name: String): Locator =
        page.getByTestId("status-monitor-card").filter(Locator.FilterOptions().setHasText(name))

    // The "start → end" timeframe pill rendered for scheduled maintenance windows (absent for manual ones).
    fun maintenanceTimeframe(): Locator = activeMaintenance.root.getByTestId("maintenance-window-timeframe")

    // The status dot in the header of a monitor's card, its label tells the status (and the maintenance)
    fun monitorStatusIndicator(name: String): Locator = monitorCardBody(name).getByTestId("monitor-status-indicator")

    // The grayed-out, non-pulsing status dot that is rendered while a monitor is under maintenance
    fun monitorMaintenanceIndicator(name: String): Locator =
        monitorCardBody(name).locator(".status-indicator.status-secondary:not(.status-indicator-animated)")

    fun title(title: String): Locator = page.getByText(title)

    // The accordions below the overall status: the active incidents, the active maintenance and the resolved incidents
    val activeIncidents: StatusPageAccordion = StatusPageAccordion("active-incidents")

    val activeMaintenance: StatusPageAccordion = StatusPageAccordion("active-maintenance")

    val resolvedIncidents: StatusPageAccordion = StatusPageAccordion("resolved-incidents")

    inner class StatusPageAccordion(private val key: String) {
        val root: Locator = page.getByTestId("status-page-$key")

        val toggle: Locator = page.getByTestId("status-page-$key-toggle")

        val count: Locator = page.getByTestId("status-page-$key-count")

        val collapse: Locator = page.locator("#status-page-$key-collapse")

        // The cards of the monitors inside an incidents accordion, in the order they are rendered
        val monitorCards: Locator = root.getByTestId("status-page-monitor-incidents")

        fun monitorCard(monitorName: String): Locator =
            monitorCards.filter(Locator.FilterOptions().setHasText(monitorName))

        /**
         * The visitor's own choice about the accordion is remembered once its animation is over, and it's kept on the
         * refreshed element of the page. Waiting for it makes sure that a refresh right after a toggle sees that
         * choice.
         */
        fun waitForRememberedChoice(isOpen: Boolean) {
            PlaywrightAssertions.assertThat(content).hasAttribute(
                "data-status-page-accordion-choices",
                Pattern.compile(".*\"$key\":$isOpen.*"),
            )
        }
    }

    // The orange badge of a monitor's card counting its incidents, which opens the incidents modal
    fun monitorIncidentsBadge(name: String): Locator = monitorCardBody(name).getByTestId("monitor-incidents-badge")

    val incidentsModal: Locator = page.getByTestId("status-page-incidents-modal")

    // The days of a monitor's history, the oldest one first
    fun trackingBlocks(name: String): Locator = monitorCardBody(name).getByTestId("tracking-block")

    // The row of category cards at the top, each linking to its own section (only rendered when there are categories)
    val categoryCards: Locator get() = page.getByTestId("category-card")

    fun categoryCard(label: String): Locator =
        categoryCards.filter(Locator.FilterOptions().setHasText(label))

    val categorySections: Locator get() = page.getByTestId("category-section")

    fun categorySection(label: String): Locator =
        categorySections.filter(
            Locator.FilterOptions().setHas(page.locator("h2", Page.LocatorOptions().setHasText(label)))
        )

    val categoryLabels: List<String>
        get() = categorySections.all().map { it.locator("h2").innerText() }

    // The multi value progress bar at the bottom of a category card, showing how its monitors are distributed
    fun categoryDistribution(label: String): Locator = categoryCard(label).getByTestId("category-distribution")

    // The segments of that bar, as `<Tabler background class> -> <width>` pairs, in the order they are rendered
    fun categoryDistributionSegments(label: String): List<Pair<String, String>> =
        categoryDistribution(label).locator(".progress-bar").all().map { segment ->
            val color = segment.getAttribute("class").orEmpty().split(" ").first { it.startsWith("bg-") }
            color to segment.evaluate("element => element.style.width").toString()
        }

    // The tooltip of the bar, spelling out the exact numbers behind the segments
    fun categoryDistributionTooltip(label: String): String? = categoryDistribution(label).tooltipText()
}
