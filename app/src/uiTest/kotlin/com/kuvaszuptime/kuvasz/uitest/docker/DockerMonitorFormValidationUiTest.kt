package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorFormModal
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.shouldAcceptAfterFixing
import com.kuvaszuptime.kuvasz.uitest.shouldRejectWith
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the Alpine.js validation and the two selects of the Docker monitor create modal.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerMonitorFormValidationUiTest : UiTestSpec() {
    init {
        "a missing Docker host is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setName("Docker Validation").setContainer("my-app").save()
            modal shouldRejectWith Messages.errorDockerHostRequired()

            modal.setDockerHost("local")
            modal shouldAcceptAfterFixing Messages.errorDockerHostRequired()
        }

        "a missing container is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setName("Docker Container").setDockerHost("local").save()
            modal shouldRejectWith Messages.errorDockerContainerRequired()

            modal.setContainer("my-app")
            modal shouldAcceptAfterFixing Messages.errorDockerContainerRequired()
        }

        "a missing name is flagged when trying to save" {
            val modal = openCreateModal()

            modal.setDockerHost("local").setContainer("my-app").save()
            modal shouldRejectWith Messages.errorNameRequired()
        }

        "an out-of-range timeout is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("Docker Timeout").setDockerHost("local").setContainer("my-app").setTimeoutMs("30001")
            modal shouldRejectWith Messages.errorTimeoutMsInvalid()

            modal.setTimeoutMs("5000")
            modal shouldAcceptAfterFixing Messages.errorTimeoutMsInvalid()
        }

        "an out-of-range uptime check interval is flagged and blocks saving" {
            val modal = openCreateModal()

            modal.setName("Docker Interval").setDockerHost("local").setContainer("my-app")
                .setUptimeCheckInterval("1")
            modal shouldRejectWith Messages.errorUptimeCheckIntervalInvalid()

            modal.setUptimeCheckInterval("60")
            modal shouldAcceptAfterFixing Messages.errorUptimeCheckIntervalInvalid()
        }

        // The hosts can only come from the YAML config, so the select offers exactly those
        "the Docker host select offers the configured hosts" {
            val modal = openCreateModal()

            modal.offeredDockerHosts shouldContainAllOf listOf("local", "vps-1")
        }

        /*
         The E2E instance points at a socket that does not exist, so the listing always fails. That is the path
         worth pinning down: an unreachable daemon must leave the operator typing a container name, not block them.
        */
        "a container can be typed in when the daemon cannot be listed" {
            val modal = openCreateModal()

            modal.setDockerHost("local")
            assertThat(modal.containerLoadFailedHint).isVisible()

            modal.setName("Docker Typed Container").setContainer("typed-container")
            assertThat(modal.selectedContainer).hasText("typed-container")
        }
    }

    private infix fun List<String>.shouldContainAllOf(expected: List<String>) {
        expected.forEach { value ->
            check(value in this) { "Expected the offered options $this to contain [$value]" }
        }
    }

    private fun openCreateModal(): DockerMonitorFormModal {
        val page = newPage()
        val list = DockerMonitorListPage(page)
        list.navigate()
        return list.openCreateModal()
    }
}
