package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The incidents overview at `/incidents`; server-rendered, scoped to the period picked in its selector.
class IncidentListPage(private val page: Page) {

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/incidents")
    }
}
