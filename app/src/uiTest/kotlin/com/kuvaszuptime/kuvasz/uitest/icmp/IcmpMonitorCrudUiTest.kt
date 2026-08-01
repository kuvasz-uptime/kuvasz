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

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned ICMP Monitor")
                .setHost("abandoned.example.com")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.hostInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val originalName = "ICMP Reset Source"
            val originalHost = "original.example.com"
            list.openCreateModal().setName(originalName).setHost(originalHost).save()
            page.waitForURL("**/icmp-monitors/*")
            val details = IcmpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("ICMP Reset Renamed")
                .setHost("changed.example.com")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.hostInput).hasValue(originalHost)
        }
    }
}
