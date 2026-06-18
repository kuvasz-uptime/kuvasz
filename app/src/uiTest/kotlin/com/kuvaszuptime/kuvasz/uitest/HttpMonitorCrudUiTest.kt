package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.HttpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.HttpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorCrudUiTest : UiTestSpec() {
    init {
        "an HTTP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = HttpMonitorListPage(page)

            list.navigate()
            assertThat(list.emptyState).isVisible()

            val originalName = "E2E Monitor"
            list.openCreateModal()
                .setName(originalName)
                .setUrl("https://example.com")
                .save()
            page.waitForURL("**/http-monitors/*")
            val details = HttpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()

            // Renaming is allowed since the monitor isn't on a status page.
            val updatedName = "E2E Monitor Renamed"
            details.openConfigureModal()
                .setName(updatedName)
                .save()
            assertThat(details.heading(updatedName)).isVisible()

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }
    }
}
