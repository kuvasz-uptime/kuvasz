package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityChecker
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.LoginPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.net.URI

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV, "enabled-connectivity-check"])
class ConnectivityIndicatorUiTest : UiTestSpec() {

    @MockBean(VersionChecker::class)
    fun versionChecker(): VersionChecker = mockk {
        every { getVersionInfo() } returns VersionInfo(
            installedVersion = "1.0.0",
            latestVersion = "2.0.0",
            latestVersionDetails = URI.create("https://kuvasz-uptime.dev/changelog#2.0.0"),
        )
    }

    @MockBean(ConnectivityChecker::class)
    fun connectivityChecker(): ConnectivityChecker = mockk(relaxed = true) {
        every { getStatus() } returns ConnectivityStatus(
            state = ConnectivityState.DOWN,
            targets = listOf("127.0.0.1:1"),
            intervalSeconds = 3600,
            timeoutSeconds = 5,
            lastCheckedAt = null,
            lastSuccessfulCheckAt = null,
            downSince = null,
            lastError = "127.0.0.1:1 (Connection refused)",
        )
    }

    init {
        "the connectivity lost badge is shown for an authenticated user in the header" {
            val page = newPage()
            DashboardPage(page).navigate()

            assertThat(page.getByRole(AriaRole.BANNER).getByTestId(BADGE_TEST_ID)).isVisible()
        }

        "the connectivity lost badge is not rendered on the login page" {
            val page = newPage(authenticated = false)
            LoginPage(page).navigate()

            assertThat(page.lightThemeToggle).isVisible()
            assertThat(page.getByTestId(BADGE_TEST_ID)).isHidden()
        }

        "the settings page has a dedicated tile with the connectivity check's state" {
            val page = newPage()
            page.navigate("/settings")

            val tile = page.getByTestId("connectivity-check-settings")
            assertThat(tile).isVisible()
            assertThat(tile).containsText("127.0.0.1:1")
        }
    }

    companion object {
        private const val BADGE_TEST_ID = "connectivity-lost-badge"
    }
}
