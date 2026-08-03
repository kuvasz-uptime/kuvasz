package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.StatusPageListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class StatusPageCrudUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
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

        "an abandoned create form is reset when the modal is reopened" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Abandoned Selection")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setTitle("Abandoned Status Page")
                .setSlug("abandoned-status-page")
            modal.selectOption("Abandoned Selection")
            assertThat(modal.selectedOptions).hasCount(1)
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again.
            val reopened = list.openCreateModal()
            assertThat(reopened.titleInput).hasValue("")
            assertThat(reopened.slugInput).hasValue("")
            // The monitor multi-select is reset along with the plain inputs.
            assertThat(reopened.selectedOptions).hasCount(0)
        }

        "edits abandoned on an existing status page are discarded when its configure modal is reopened" {
            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            val originalTitle = "Status Page Reset Source"
            val originalSlug = "status-page-reset-source"
            list.openCreateModal().setTitle(originalTitle).setSlug(originalSlug).save()
            page.waitForURL("**/status-pages/*")
            val details = StatusPageDetailsPage(page)

            details.openConfigureModal()
                .setTitle("Status Page Reset Renamed")
                .setSlug("changed-slug")
                .dismiss()

            val reopened = details.openConfigureModal()
            assertThat(reopened.titleInput).hasValue(originalTitle)
            assertThat(reopened.slugInput).hasValue(originalSlug)
        }

        "the status-page modal's monitors select adds a seeded monitor" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Selectable Monitor")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("Selectable Monitor")
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Selectable Monitor")
        }

        "the status-page modal offers the monitors sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createHttpMonitor(httpMonitorRepository, monitorName = it) }

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.monitorOptions).hasCount(names.size)
            modal.monitorOptionNames shouldBe listOf("http:alpha", "http:bravo", "http:Charlie", "http:Delta")
        }

        "a status page can be published and unpublished from the list" {
            createStatusPage(
                dslContext,
                title = "List Toggle Status Page",
                slug = "list-toggle-status-page",
                public = true,
            )

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            assertThat(list.publicIndicator("List Toggle Status Page")).isVisible()

            list.toggleVisibility("List Toggle Status Page")
            assertThat(list.privateIndicator("List Toggle Status Page")).isVisible()

            list.toggleVisibility("List Toggle Status Page")
            assertThat(list.publicIndicator("List Toggle Status Page")).isVisible()
        }

        "a status page can be published and unpublished from its detail page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "SP Monitor")
            val statusPage = createStatusPage(
                dslContext,
                title = "Toggle Status Page",
                slug = "toggle-status-page",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val details = StatusPageDetailsPage(page)
            details.navigate(statusPage.id)
            assertThat(details.visibilityBadge(Messages.public())).isVisible()

            details.visibilityToggleButton.click()
            assertThat(details.visibilityBadge(Messages.private())).isVisible()
        }

        "the status page list is sorted by title, regardless of its casing" {
            val titles = listOf("Charlie", "bravo", "Delta", "alpha")
            titles.forEach { createStatusPage(dslContext, title = it, slug = it.lowercase()) }

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()

            // The table is HTMX-swapped in, so wait for every row before reading their order.
            assertThat(list.rows).hasCount(titles.size)
            list.titles shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }
    }
}
