package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.LoadState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerMonitorCrudUiTest(private val dockerMonitorRepository: DockerMonitorRepository) : UiTestSpec() {
    init {
        /*
         Docker is the one type whose ignoreConnectivityCheck defaults to on, and the create form has to start
         from that default rather than from the value every other type uses.
        */
        "a monitor created through the UI ignores the connectivity check by default" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val name = "E2E Docker Connectivity Default"
            // The toggle itself is only rendered while the connectivity check is on, but the value is submitted
            // either way, so this asserts what actually reaches the database
            list.openCreateModal().setName(name).setDockerHost("local").setContainer("my-app").save()
            page.waitForURL("**/docker-monitors/*")

            dockerMonitorRepository.findByName(name).shouldNotBeNull().ignoreConnectivityCheck shouldBe true
        }

        "restart alerts are off by default, and turning them on survives the save" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val name = "E2E Docker Restart Alerts"
            val modal = list.openCreateModal().setName(name).setDockerHost("local").setContainer("my-app")
            assertThat(modal.restartAlertToggle).not().isChecked()
            modal.enableRestartAlerts().save()
            page.waitForURL("**/docker-monitors/*")

            dockerMonitorRepository.findByName(name).shouldNotBeNull().restartAlertEnabled shouldBe true
            assertThat(DockerMonitorDetailsPage(page).openConfigureModal().restartAlertToggle).isChecked()
        }

        "a Docker monitor can be created, edited and deleted entirely through the UI" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            val originalName = "E2E Docker Monitor"
            list.openCreateModal()
                .setName(originalName)
                .setDockerHost("local")
                .setContainer("my-app")
                .setCategory("Payments")
                .save()
            page.waitForURL("**/docker-monitors/*")
            val details = DockerMonitorDetailsPage(page)
            assertThat(details.heading(originalName)).isVisible()
            assertThat(details.categoryBadge).containsText("Payments")

            val updatedName = "E2E Docker Monitor Renamed"
            val configureModal = details.openConfigureModal()
            // The two type specific fields come back pre-filled from the monitor
            assertThat(configureModal.selectedDockerHost).hasText("local")
            assertThat(configureModal.selectedContainer).hasText("my-app")
            configureModal.setName(updatedName).setCategory("").save()
            assertThat(details.heading(updatedName)).isVisible()

            list.navigate()
            assertThat(list.rowByName(updatedName)).isVisible()

            list.deleteMonitor(updatedName)
            assertThat(list.rowByName(updatedName)).hasCount(0)
            assertThat(list.emptyState).isVisible()
        }

        "the host and the container of an existing monitor survive an edit that does not touch them" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val name = "E2E Docker Untouched"
            list.openCreateModal()
                .setName(name)
                .setDockerHost("vps-1")
                .setContainer("worker")
                .save()
            page.waitForURL("**/docker-monitors/*")

            // Editing through the list's shared modal, which is the path that re-populates the selects
            list.navigate()
            // The save reloads the list in place, so navigating before that reload has started would get aborted by it
            page.waitForRequest({ it.isNavigationRequest }) {
                list.configureMonitor(name).setUptimeCheckInterval("120").save()
            }

            list.navigate()
            val reopened = list.configureMonitor(name)
            assertThat(reopened.selectedDockerHost).hasText("vps-1")
            assertThat(reopened.selectedContainer).hasText("worker")
        }

        /*
         The details page renders its form already populated, but the daemon is only asked once the modal opens.
         The E2E daemon can never be listed, so the hint is the proof that the listing was attempted at all.
        */
        "the containers of the stored host are listed when the details page's modal is opened" {
            val monitor = createDockerMonitor(dockerMonitorRepository, monitorName = "E2E Docker Listing")
            val page = newPage()
            val listingRequests = mutableListOf<String>()
            page.onRequest { request -> if (request.url().endsWith("/containers")) listingRequests += request.url() }
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)
            page.waitForLoadState(LoadState.NETWORKIDLE)
            listingRequests.shouldBeEmpty()

            val modal = details.openConfigureModal()
            assertThat(modal.selectedContainer).hasText(monitor.container)
            assertThat(modal.containerLoadFailedHint).isVisible()
        }

        "a monitor keeps a Docker host that was removed from the config through an edit" {
            val monitor = createDockerMonitor(
                dockerMonitorRepository,
                monitorName = "E2E Docker Dangling",
                dockerHost = "removed-host",
            )
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor(monitor.name)
            assertThat(modal.selectedDockerHost).containsText("removed-host")
            val response = page.waitForResponse({ it.request().method() == "PATCH" }) {
                modal.setUptimeCheckInterval("$UPDATED_UPTIME_CHECK_INTERVAL").save()
            }
            response.ok() shouldBe true

            with(dockerMonitorRepository.findByName(monitor.name).shouldNotBeNull()) {
                dockerHost shouldBe "removed-host"
                uptimeCheckInterval shouldBe UPDATED_UPTIME_CHECK_INTERVAL
            }
        }

        // Only the monitor that already has such a host may keep it, a new one has to pick a configured host
        "a clone of a monitor whose Docker host was removed from the config has to pick a configured one" {
            val monitor = createDockerMonitor(
                dockerMonitorRepository,
                monitorName = "E2E Docker Dangling Source",
                dockerHost = "removed-host",
            )
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val cloneModal = list.cloneMonitor(monitor.name)
            cloneModal.save()
            cloneModal shouldRejectWith Messages.errorDockerHostNotConfigured()

            cloneModal.setDockerHost("local")
            cloneModal shouldAcceptAfterFixing Messages.errorDockerHostNotConfigured()
        }

        "an abandoned create form is reset when the modal is reopened" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal()
                .setName("Abandoned Docker Monitor")
                .setDockerHost("local")
                .setContainer("abandoned-container")
                .setUptimeCheckInterval("300")
            modal.dismiss()

            // Closing the modal fires the reset event, so the next open starts from the defaults again
            val reopened = list.openCreateModal()
            assertThat(reopened.nameInput).hasValue("")
            assertThat(reopened.uptimeCheckIntervalInput).hasValue("60")
            assertThat(reopened.selectedDockerHost).hasCount(0)
            assertThat(reopened.selectedContainer).hasCount(0)
        }

        "a Docker monitor can be cloned from the list, pre-filling a fresh create form" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val sourceName = "Docker Clone Source"
            list.openCreateModal()
                .setName(sourceName)
                .setDockerHost("local")
                .setContainer("my-app")
                .setUptimeCheckInterval("90")
                .save()
            page.waitForURL("**/docker-monitors/*")

            list.navigate()
            val clonedName = Messages.clonedMonitorName(sourceName)
            val cloneModal = list.cloneMonitor(sourceName)
            assertThat(cloneModal.nameInput).hasValue(clonedName)
            assertThat(cloneModal.selectedDockerHost).hasText("local")
            assertThat(cloneModal.selectedContainer).hasText("my-app")
            assertThat(cloneModal.uptimeCheckIntervalInput).hasValue("90")

            cloneModal.save()
            page.waitForURL("**/docker-monitors/*")

            list.navigate()
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName(clonedName)).hasCount(1)
        }
    }

    companion object {
        private const val UPDATED_UPTIME_CHECK_INTERVAL = 120
    }
}
