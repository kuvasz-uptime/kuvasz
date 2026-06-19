package com.kuvaszuptime.kuvasz.uitest.push

import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PushMonitorCrudUiTest : UiTestSpec() {
    init {
        "a push monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = PushMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: only the name is required — the client secret is auto-generated and the interval defaults to 10.
            val originalName = "E2E Push Monitor"
            list.openCreateModal().setName(originalName).save()
            page.waitForURL("**/push-monitors/*")
            val details = PushMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()

            val updatedName = "E2E Push Monitor Renamed"
            details.openConfigureModal().setName(updatedName).save()
            assertThat(details.heading(updatedName)).isVisible()

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }
    }
}
