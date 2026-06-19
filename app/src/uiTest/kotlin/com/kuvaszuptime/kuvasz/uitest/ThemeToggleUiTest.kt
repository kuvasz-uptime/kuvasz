package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the dark/light theme switcher, which flips `data-bs-theme` and persists the choice in localStorage.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ThemeToggleUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "toggling the theme switches the color scheme and persists it across reloads - dashboard" {
            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            // Default = dark
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")

            page.lightThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "light")

            // Change to light
            page.reload()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "light")

            page.darkThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")

            // Change it back to dark
            page.reload()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")
        }

        "toggling the theme switches the color scheme and persists it across reloads - status page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Public API")
            val pageTitle = "Public Systems Status"
            val slug = "public-systems"
            createStatusPage(
                dslContext,
                title = pageTitle,
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // Default = dark
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")

            page.lightThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "light")

            // Change to light
            page.reload()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "light")

            page.darkThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")

            // Change it back to dark
            page.reload()
            assertThat(page.htmlRoot).hasAttribute("data-bs-theme", "dark")
        }
    }
}
