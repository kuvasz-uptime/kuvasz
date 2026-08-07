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
}
