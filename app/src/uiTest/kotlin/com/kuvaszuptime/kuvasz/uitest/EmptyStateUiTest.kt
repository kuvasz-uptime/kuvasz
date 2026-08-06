package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.IncidentListPage
import com.kuvaszuptime.kuvasz.uitest.pages.IntegrationListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * The placeholders of the two lists nothing can be added to from the UI: incidents are recorded automatically and
 * integrations can only come from the YAML config, so neither of them has a CRUD spec that could host these.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class EmptyStateUiTest : UiTestSpec() {
    init {
        "the incidents page explains that nothing happened in the selected period" {
            val page = newPage()
            val incidents = IncidentListPage(page)

            incidents.navigate()

            assertThat(incidents.emptyState).containsText(Messages.noIncidents())
            assertThat(incidents.emptyState).containsText(Messages.noIncidentsInPeriod())
        }

        "the integrations page points at the YAML configuration file" {
            val page = newPage()
            val integrations = IntegrationListPage(page)

            integrations.navigate()

            assertThat(integrations.emptyState).containsText(Messages.noIntegrationsYet())
            assertThat(integrations.emptyState).containsText(Messages.noIntegrationsDescription())
            assertThat(integrations.rows).hasCount(0)
        }
    }
}
