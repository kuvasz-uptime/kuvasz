package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class IntegrationListPage(private val page: Page) {

    val rows: Locator get() = page.getByTestId("integration-row")

    // Replaces the table when the YAML config doesn't declare a single integration
    val emptyState: Locator get() = page.getByTestId("empty-state")

    val ids: List<String> get() = rows.locator("code").allInnerTexts().map { it.trim() }

    fun navigate() {
        page.navigate("/integrations")
    }

    /**
     * The content of the details tooltip next to the ID of an integration (if any), which Bootstrap moves out of
     * `title` once it's set up
     */
    fun detailsTooltipOf(integrationId: String): String? {
        val toggler = rows
            .filter(Locator.FilterOptions().setHasText(integrationId))
            .locator("td:has(code) [data-bs-toggle='tooltip']")

        return if (toggler.count() == 0) {
            null
        } else {
            toggler.getAttribute("data-bs-original-title") ?: toggler.getAttribute("title")
        }
    }
}
