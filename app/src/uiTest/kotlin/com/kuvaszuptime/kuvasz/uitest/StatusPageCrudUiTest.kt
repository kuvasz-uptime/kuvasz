package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.StatusPageDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.StatusPageListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageCrudUiTest : UiTestSpec() {
    init {
        "a status page can be created and edited through the UI via an editable upsert modal" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val title = "E2E Status Page"
            list.openCreateModal()
                .setTitle(title)
                .setSlug("e2e-status-page")
                .save()
            page.waitForURL("**/status-pages/*")
            val details = StatusPageDetailsPage(page)
            assertThat(details.heading(title)).isVisible()
            assertThat(details.content).isVisible()
            assertThat(details.configureButton).isVisible()

            // Contrast with the read-only case: the upsert modal here is editable, with Save available.
            val updatedTitle = "E2E Status Page Renamed"
            val modal = details.openConfigureModal()
            assertThat(modal.titleInput).isEnabled()
            assertThat(modal.saveButton).isVisible()
            modal.setTitle(updatedTitle).save()
            assertThat(details.heading(updatedTitle)).isVisible()
        }
    }
}
