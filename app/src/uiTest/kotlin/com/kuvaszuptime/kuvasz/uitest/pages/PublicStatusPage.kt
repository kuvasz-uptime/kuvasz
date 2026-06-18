package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A public status page at `/status/{slug}` — reachable without authentication when the page is marked public.
class PublicStatusPage(private val page: Page) {

    fun navigate(slug: String) {
        page.navigate("/status/$slug")
    }

    // Each monitor is rendered as a card whose name is an `<h3>` heading.
    fun monitorCard(name: String): Locator = page.byRole(AriaRole.HEADING, name)

    // The whole monitor card (status badge, uptime ratio, tracking blocks) for the named monitor.
    fun monitorCardBody(name: String): Locator =
        page.locator(".card").filter(Locator.FilterOptions().setHasText(name))

    fun title(title: String): Locator = page.getByText(title)
}
