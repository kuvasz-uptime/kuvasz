package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

class IntegrationListPage(private val page: Page) {

    val rows: Locator get() = page.getByTestId("integration-row")

    val ids: List<String> get() = rows.locator("code").allInnerTexts().map { it.trim() }

    fun navigate() {
        page.navigate("/integrations")
    }
}
