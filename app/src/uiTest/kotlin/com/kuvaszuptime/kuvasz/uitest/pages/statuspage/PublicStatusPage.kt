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

    fun monitorCardBody(name: String): Locator =
        page.getByTestId("status-monitor-card").filter(Locator.FilterOptions().setHasText(name))

    fun title(title: String): Locator = page.getByText(title)
}
