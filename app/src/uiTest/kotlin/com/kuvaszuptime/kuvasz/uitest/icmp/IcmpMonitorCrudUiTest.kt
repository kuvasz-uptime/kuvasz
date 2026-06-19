package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorCrudUiTest : UiTestSpec() {
    init {
        "an ICMP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: name + host are required, the rest default to valid values.
            val originalName = "E2E ICMP Monitor"
            list.openCreateModal().setName(originalName).setHost("127.0.0.1").save()
            page.waitForURL("**/icmp-monitors/*")
            val details = IcmpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()

            val updatedName = "E2E ICMP Monitor Renamed"
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
