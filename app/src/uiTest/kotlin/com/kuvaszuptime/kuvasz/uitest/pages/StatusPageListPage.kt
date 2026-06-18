package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The status pages list at `/status-pages`; the table is swapped into `#status-page-list` via HTMX.
class StatusPageListPage(private val page: Page) {

    val addButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.addNewStatusPage())

    fun navigate() {
        page.navigate("/status-pages")
    }

    fun openCreateModal(): StatusPageFormModal {
        addButton.click()
        return StatusPageFormModal(page)
    }
}
