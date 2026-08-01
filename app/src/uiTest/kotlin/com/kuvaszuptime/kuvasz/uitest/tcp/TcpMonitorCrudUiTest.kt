package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class TcpMonitorCrudUiTest : UiTestSpec() {
    init {
        "a TCP monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Create: name + host + port are required, the rest default to valid values.
            val originalName = "E2E TCP Monitor"
            list.openCreateModal().setName(originalName).setHost("127.0.0.1").setPort("5432").save()
            page.waitForURL("**/tcp-monitors/*")
            val details = TcpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()

            val updatedName = "E2E TCP Monitor Renamed"
            details.openConfigureModal().setName(updatedName).save()
            assertThat(details.heading(updatedName)).isVisible()

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }

        "a TCP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val sourceName = "TCP Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setHost("127.0.0.1")
                .setPort("8080")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/tcp-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.hostInput).hasValue("127.0.0.1")
            assertThat(cloneModal.portInput).hasValue("8080")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/tcp-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned TCP Monitor")
                .setHost("abandoned.example.com")
                .setPort("9999")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.hostInput).hasValue("")
            assertThat(reopened.portInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val originalName = "TCP Reset Source"
            val originalHost = "original.example.com"
            list.openCreateModal().setName(originalName).setHost(originalHost).setPort("5432").save()
            page.waitForURL("**/tcp-monitors/*")
            val details = TcpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("TCP Reset Renamed")
                .setHost("changed.example.com")
                .setPort("9999")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.hostInput).hasValue(originalHost)
            assertThat(reopened.portInput).hasValue("5432")
        }
    }
}
