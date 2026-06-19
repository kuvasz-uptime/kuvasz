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
    }
}
