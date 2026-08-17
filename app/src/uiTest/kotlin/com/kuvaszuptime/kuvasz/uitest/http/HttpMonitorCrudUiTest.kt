package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
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
                .setCategory("Payments")
                .save()
            page.waitForURL("**/http-monitors/*")
            val details = HttpMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            // The category is shown as a badge in the header
            assertThat(details.categoryBadge).containsText("Payments")

            // Renaming is allowed since the monitor isn't on a status page.
            val updatedName = "E2E Monitor Renamed"
            val configureModal = details.openConfigureModal()
            // The category is pre-filled from the monitor and can be cleared
            assertThat(configureModal.categoryInput).hasValue("Payments")
            configureModal
                .setName(updatedName)
                .setCategory("")
                .save()
            assertThat(details.heading(updatedName)).isVisible()
            assertThat(details.categoryBadge).hasCount(0)

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }

        "an HTTP monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // Seed a source monitor with a couple of non-default values.
            val sourceName = "HTTP Clone Source"
            val sourceUrl = "https://clone-source.example.com"
            list.openCreateModal()
                .setName(sourceName)
                .setUrl(sourceUrl)
                .setUptimeCheckInterval("120")
                .save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            // Every field is pre-filled from the source, except the name which is suggested as "Copy of <name>".
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.urlInput).hasValue(sourceUrl)
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("120")

            cloneModal.save()
            page.waitForURL("**/http-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }

        "the HTTP monitor modal's accepted-status-codes select adds a chosen code" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal().expandEvaluationSettings()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("200")
            assertThat(modal.selectedOptions).hasCount(1)
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned HTTP Monitor")
                .setUrl("https://abandoned.example.com")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.urlInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
        }

        "edits abandoned on an existing monitor are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val originalName = "HTTP Reset Source"
            val originalUrl = "https://original.example.com"
            list.openCreateModal().setName(originalName).setUrl(originalUrl).save()
            page.waitForURL("**/http-monitors/*")
            val details = HttpMonitorDetailsPage(page)

            details.openConfigureModal()
                .setName("HTTP Reset Renamed")
                .setUrl("https://changed.example.com")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.nameInput).hasValue(originalName)
            assertThat(reopened.urlInput).hasValue(originalUrl)
        }
    }
}
