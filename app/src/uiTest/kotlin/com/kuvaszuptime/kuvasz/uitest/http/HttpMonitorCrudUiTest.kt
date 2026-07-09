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
    }
}
