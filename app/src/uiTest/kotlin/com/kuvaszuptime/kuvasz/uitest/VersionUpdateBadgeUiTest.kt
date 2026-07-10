package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.LoginPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.net.URI

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class VersionUpdateBadgeUiTest : UiTestSpec() {

    @MockBean(VersionChecker::class)
    fun versionChecker(): VersionChecker = mockk {
        every { getVersionInfo() } returns VersionInfo(
            installedVersion = "1.0.0",
            latestVersion = "2.0.0",
            latestVersionDetails = URI.create("https://kuvasz-uptime.dev/changelog#2.0.0"),
        )
    }

    init {
        "the version update badge is shown for an authenticated user in the header" {
            val page = newPage()
            DashboardPage(page).navigate()

            // Scoped to the header, since the badge is also rendered in the (authenticated-only) footer
            assertThat(page.getByRole(AriaRole.BANNER).getByTestId(BADGE_TEST_ID)).isVisible()
        }

        "the version update badge is not rendered on the login page" {
            val page = newPage(authenticated = false)
            LoginPage(page).navigate()

            // The header itself is rendered (theme toggle is present), but the badge is guarded behind authentication
            assertThat(page.lightThemeToggle).isVisible()
            assertThat(page.getByTestId(BADGE_TEST_ID)).isHidden()
        }
    }

    companion object {
        private const val BADGE_TEST_ID = "version-update-badge"
    }
}
