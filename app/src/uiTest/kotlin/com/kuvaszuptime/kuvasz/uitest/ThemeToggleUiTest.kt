package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the dark/light theme switcher, which flips `data-bs-theme` and persists the choice in localStorage.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ThemeToggleUiTest : UiTestSpec() {
    init {
        "toggling the theme switches the color scheme and persists it across reloads" {
            val page = newPage()
            DashboardPage(page).navigate()
            val html = page.locator("html")

            assertThat(html).hasAttribute("data-bs-theme", "dark")

            // In dark mode only the "switch to light" control is visible.
            page.locator("[onclick=\"setTheme('light')\"]").click()
            assertThat(html).hasAttribute("data-bs-theme", "light")

            page.reload()
            assertThat(html).hasAttribute("data-bs-theme", "light")
        }
    }
}
