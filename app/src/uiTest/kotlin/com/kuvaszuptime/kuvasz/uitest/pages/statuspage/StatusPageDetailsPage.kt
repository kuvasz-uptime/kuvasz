package com.kuvaszuptime.kuvasz.uitest.pages.statuspage

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// A status page's detail page at `/status-pages/{id}`.
class StatusPageDetailsPage(private val page: Page) {

    fun heading(title: String): Locator = page.byRole(AriaRole.HEADING, title)

    val configureButton: Locator get() = page.getByTestId("configure-button")

    val content: Locator get() = page.locator("#status-page-details-content")

    // The publish/unpublish button in the header (a reload follows the visibility change).
    val visibilityToggleButton: Locator get() = page.getByTestId("toggle-visibility-button")

    // The "Public" / "Private" badge in the header (scoped to avoid the same label inside the update modal).
    fun visibilityBadge(text: String): Locator =
        page.locator("#status-page-detail-heading").getByText(text, Locator.GetByTextOptions().setExact(true))

    fun navigate(statusPageId: Long) {
        page.navigate("/status-pages/$statusPageId")
    }

    fun openConfigureModal(): StatusPageFormModal {
        configureButton.click()
        return StatusPageFormModal(page)
    }
}
