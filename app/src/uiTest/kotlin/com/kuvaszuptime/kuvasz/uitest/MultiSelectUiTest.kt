package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.HttpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.pages.StatusPageListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * Exercises the TomSelect multi-selects: the accepted-status-codes picker in the HTTP monitor modal and the monitors
 * picker in the status-page modal.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MultiSelectUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "the HTTP monitor modal's accepted-status-codes select adds a chosen code" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            val modal = list.openCreateModal().expandEvaluationSettings()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("200")
            assertThat(modal.selectedOptions).hasCount(1)
        }

        "the status-page modal's monitors select adds a seeded monitor" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Selectable Monitor")

            val page = newPage()
            val list = StatusPageListPage(page)
            list.navigate()
            val modal = list.openCreateModal()

            assertThat(modal.selectedOptions).hasCount(0)
            modal.selectOption("Selectable Monitor")
            assertThat(modal.selectedOptions).hasCount(1)
            assertThat(modal.selectedOptions).containsText("Selectable Monitor")
        }
    }
}
