package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerHostSettingsUiTest : UiTestSpec() {

    // Neither E2E host is a real daemon, so a negotiated version can only come from here
    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk {
        every { negotiatedVersions() } returns mapOf("vps-1" to "1.44")
    }

    init {
        "the settings page lists every configured Docker host with its auth method and API version" {
            val page = newPage()
            page.navigate("/settings")

            val rows = page.getByTestId("docker-hosts-settings").getByTestId("docker-host-settings-row")
            assertThat(rows).hasCount(2)

            with(page.hostRow("local")) {
                assertThat(this).containsText(Messages.dockerHostAuthUnixSocket())
                assertThat(getByTestId("docker-host-api-version")).hasText(Messages.dockerHostApiVersionUnknown())
            }
            with(page.hostRow("vps-1")) {
                assertThat(this).containsText(Messages.dockerHostAuthNone())
                assertThat(getByTestId("docker-host-api-version")).hasText(Messages.dockerHostApiVersion("1.44"))
            }
        }
    }

    private fun Page.hostRow(name: String): Locator =
        getByTestId("docker-host-settings-row").filter(Locator.FilterOptions().setHasText(name))
}
