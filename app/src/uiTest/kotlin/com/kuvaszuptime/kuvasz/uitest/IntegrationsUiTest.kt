package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.uitest.pages.IntegrationListPage
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV, "ui-test-integrations"])
class IntegrationsUiTest : UiTestSpec() {
    init {
        "the integrations page lists the integrations sorted by name, regardless of their casing" {
            val page = newPage()
            val list = IntegrationListPage(page)
            list.navigate()

            assertThat(list.rows).hasCount(CONFIGURED_INTEGRATIONS.size)
            list.ids shouldBe listOf("slack:alpha", "slack:bravo", "slack:Charlie", "slack:Delta")
        }

        "the integration checkboxes of a monitor form are sorted by name, regardless of their casing" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.integrationCheckboxes).hasCount(CONFIGURED_INTEGRATIONS.size)
            modal.integrationCheckboxNames shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }

        "the integration badges of a maintenance window are sorted by name, regardless of their casing" {
            val window = createMaintenanceWindow(
                dslContext,
                name = "Integration Badge Window",
                integrations = CONFIGURED_INTEGRATIONS.map { IntegrationID(IntegrationType.SLACK, it) },
            )

            val page = newPage()
            val details = MaintenanceWindowDetailsPage(page)
            details.navigate(window.id)

            details.badgeTextsOf(Messages.integrationsLabel()) shouldBe
                listOf("slack:alpha", "slack:bravo", "slack:Charlie", "slack:Delta")
        }
    }

    companion object {
        // The integrations declared by `application-ui-test-integrations.yml`
        private val CONFIGURED_INTEGRATIONS = listOf("Charlie", "bravo", "Delta", "alpha")
    }
}
