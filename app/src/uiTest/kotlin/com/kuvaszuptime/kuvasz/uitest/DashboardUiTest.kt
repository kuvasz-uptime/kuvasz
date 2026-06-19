package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DashboardUiTest : UiTestSpec() {
    init {
        "the authenticated dashboard renders and HTMX swaps in the HTTP monitoring stats" {
            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.heading).isVisible()
            // The section header and stat cards only appear once the HTMX `load` swap has replaced the spinner.
            assertThat(dashboard.httpSectionHeader).isVisible()
            assertThat(dashboard.httpStatCards.first()).isVisible()
        }
    }
}
