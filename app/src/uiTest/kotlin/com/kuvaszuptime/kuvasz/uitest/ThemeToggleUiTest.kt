package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.settings.SettingsAppearancePage
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

private const val DATA_ATTRIBUTE = "data-bs-theme"
private const val BASE_ATTRIBUTE = "data-bs-theme-base"
private const val PRIMARY_ATTRIBUTE = "data-bs-theme-primary"

// Exercises the dark/light theme switcher, which flips `data-bs-theme` and persists the choice in localStorage, and the
// gray palette and accent color pickers of the Settings page, which work the same way, but only in the admin UI.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ThemeToggleUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "toggling the theme switches the color scheme and persists it across reloads - dashboard" {
            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            // Default = dark
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")

            page.lightThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "light")

            // Change to light
            page.reload()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "light")

            page.darkThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")

            // Change it back to dark
            page.reload()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")
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
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")

            page.lightThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "light")

            // Change to light
            page.reload()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "light")

            page.darkThemeToggle.click()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")

            // Change it back to dark
            page.reload()
            assertThat(page.htmlRoot).hasAttribute(DATA_ATTRIBUTE, "dark")
        }

        "the gray palette and the accent color picked on the Settings page are applied and persisted" {
            val page = newPage()
            val settings = SettingsAppearancePage(page)
            settings.navigate()

            // The defaults: the blue-tinted gray palette Tabler used before 1.6, and its default accent color
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "gray")
            assertThat(settings.optionRadio("base", "gray")).isChecked()
            assertThat(settings.optionRadio("primary", "blue")).isChecked()

            settings.pick("base", "slate")
            settings.pick("primary", "teal")
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "slate")
            assertThat(page.htmlRoot).hasAttribute(PRIMARY_ATTRIBUTE, "teal")

            // Preselected after a reload
            page.reload()
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "slate")
            assertThat(page.htmlRoot).hasAttribute(PRIMARY_ATTRIBUTE, "teal")
            assertThat(settings.optionRadio("base", "slate")).isChecked()
            assertThat(settings.optionRadio("primary", "teal")).isChecked()

            // Applied on the other pages as well
            DashboardPage(page).navigate()
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "slate")
            assertThat(page.htmlRoot).hasAttribute(PRIMARY_ATTRIBUTE, "teal")
        }

        "an unknown saved theme option is ignored" {
            val page = newPage()
            val settings = SettingsAppearancePage(page)
            settings.navigate()
            page.evaluate(
                "localStorage.setItem('kuvasz-theme-base', 'bogus'); " +
                    "localStorage.setItem('kuvasz-theme-primary', 'bogus')"
            )

            page.reload()
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "gray")
            assertThat(page.htmlRoot).not().hasAttribute(PRIMARY_ATTRIBUTE, "bogus")
            assertThat(settings.optionRadio("primary", "blue")).isChecked()
        }

        "the theme options picked in the admin UI don't restyle the public status pages" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Styled API")
            val slug = "styled-systems"
            createStatusPage(
                dslContext,
                title = "Styled Systems Status",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val settings = SettingsAppearancePage(page)
            settings.navigate()
            settings.pick("base", "zinc")
            settings.pick("primary", "red")

            PublicStatusPage(page).navigate(slug)
            assertThat(page.htmlRoot).hasAttribute(BASE_ATTRIBUTE, "gray")
            assertThat(page.htmlRoot).not().hasAttribute(PRIMARY_ATTRIBUTE, "red")
        }
    }
}
