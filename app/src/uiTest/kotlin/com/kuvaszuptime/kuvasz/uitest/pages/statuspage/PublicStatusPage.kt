package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A public status page at `/status/{slug}` — reachable without authentication when the page is marked public.
class PublicStatusPage(private val page: Page) {

    fun navigate(slug: String) {
        page.navigate("/status/$slug")
    }

    fun monitorCard(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    val monitorCards: Locator get() = page.getByTestId("status-monitor-card")

    val monitorNames: List<String> get() = monitorCards.locator("h3").allInnerTexts().map { it.trim() }

    fun monitorCardBody(name: String): Locator =
        page.getByTestId("status-monitor-card").filter(Locator.FilterOptions().setHasText(name))

    fun maintenanceBanner(): Locator = page.getByTestId("status-page-maintenance-banner")

    // The "start → end" timeframe pill rendered for scheduled maintenance windows (absent for manual ones).
    fun maintenanceTimeframe(): Locator = maintenanceBanner().getByTestId("maintenance-window-timeframe")

    // The grayed-out uptime badge (with a tool icon) that is rendered while a monitor is under maintenance.
    fun monitorMaintenanceBadge(name: String): Locator = monitorCardBody(name).locator(".status.status-gray")

    fun title(title: String): Locator = page.getByText(title)

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

    // The tooltip of the bar, spelling out the exact numbers behind the segments. Bootstrap moves the rendered
    // `title` into `data-bs-original-title` when it takes the element over, so that one wins when it's there.
    fun categoryDistributionTooltip(label: String): String? = categoryDistribution(label).let { bar ->
        bar.getAttribute("data-bs-original-title") ?: bar.getAttribute("title")
    }
}
