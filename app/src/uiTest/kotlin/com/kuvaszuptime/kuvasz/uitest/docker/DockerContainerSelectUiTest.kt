package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.services.docker.DockerContainer
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerListing
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorDetailsPage
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerContainerSelectUiTest(private val dockerMonitorRepository: DockerMonitorRepository) : UiTestSpec() {

    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk {
        every { listContainers(any(), any()) } returns DockerContainerListing.Listed(
            listOf(
                DockerContainer(name = "web", id = "id-web", image = "nginx:alpine", state = "running"),
                DockerContainer(name = "worker", id = "id-worker", image = "busybox:1.37", state = "exited"),
            )
        )
        every { listContainers(match { it.name == "vps-1" }, any()) } returns DockerContainerListing.Listed(
            listOf(DockerContainer(name = "api", id = "id-api", image = "httpd:2.4", state = "running"))
        )
    }

    init {
        "the opened container dropdown shows the image and the state of each container, the selection only its name" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val modal = list.openCreateModal().setDockerHost("local").openContainerDropdown()

            with(modal.containerOption("web")) {
                assertThat(this).containsText("nginx:alpine")
                assertThat(locator(".badge")).hasText("running")
            }
            with(modal.containerOption("worker")) {
                assertThat(this).containsText("busybox:1.37")
                assertThat(locator(".badge")).hasText("exited")
            }

            modal.setContainer("web")
            assertThat(modal.selectedContainer).hasText("web")
        }

        "the container a monitor already watches is offered with its details too, when the monitor is edited" {
            val monitor = createDockerMonitor(dockerMonitorRepository, dockerHost = "local", container = "web")
            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            val modal = details.openConfigureModal().openContainerDropdown()

            with(modal.containerOption("web")) {
                assertThat(this).containsText("nginx:alpine")
                assertThat(locator(".badge")).hasText("running")
            }
            assertThat(modal.selectedContainer).hasText("web")
        }

        "the container a monitor already watches is offered with its details, when edited from the list" {
            val monitor = createDockerMonitor(dockerMonitorRepository, dockerHost = "local", container = "web")
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val modal = list.configureMonitor(monitor.name).openContainerDropdown()

            with(modal.containerOption("web")) {
                assertThat(this).containsText("nginx:alpine")
                assertThat(locator(".badge")).hasText("running")
            }
        }

        "switching the host of an edited monitor drops its container, and switching back restores it" {
            val monitor = createDockerMonitor(dockerMonitorRepository, dockerHost = "vps-1", container = "api")
            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            val modal = details.openConfigureModal()
            assertThat(modal.selectedContainer).hasText("api")

            modal.setDockerHost("local")
            assertThat(modal.selectedContainer).hasCount(0)
            modal.openContainerDropdown()
            assertThat(modal.containerOption("web")).isVisible()
            assertThat(modal.containerOption("api")).hasCount(0)

            modal.setDockerHost("vps-1")
            assertThat(modal.selectedContainer).hasText("api")
        }
    }
}
