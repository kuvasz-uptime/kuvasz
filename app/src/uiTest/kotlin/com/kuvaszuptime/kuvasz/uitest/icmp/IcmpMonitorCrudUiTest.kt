package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
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

        "an ICMP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val sourceName = "ICMP Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setHost("127.0.0.1")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/icmp-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.hostInput).hasValue("127.0.0.1")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/icmp-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }
    }
}
