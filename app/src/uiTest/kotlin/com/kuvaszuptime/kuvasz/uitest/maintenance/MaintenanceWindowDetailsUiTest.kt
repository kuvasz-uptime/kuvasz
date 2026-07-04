package com.kuvaszuptime.kuvasz.uitest.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowDetailsUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a maintenance window can be disabled from its detail page" {
            val window = createMaintenanceWindow(dslContext, name = "Detail Toggle Window", enabled = true)

            val page = newPage()
            val details = MaintenanceWindowDetailsPage(page)
            details.navigate(window.id)
            // An enabled manual window is currently active (green dot).
            assertThat(details.statusIndicator("status-green")).isVisible()

            details.toggleButton.click()
            // After disabling, the live heading refreshes to the paused (cyan) state.
            assertThat(details.statusIndicator("status-cyan")).isVisible()
        }

        "a global window shows the all-monitors badge in the affected-monitors row" {
            val window = createMaintenanceWindow(dslContext, name = "Global Window", global = true)

            val page = newPage()
            val details = MaintenanceWindowDetailsPage(page)
            details.navigate(window.id)
            assertThat(details.detailRow(Messages.maintenanceWindowAffectedMonitors()))
                .containsText(Messages.maintenanceWindowGlobalScope())
        }

        "a monitor-scoped window links to each affected monitor's detail page" {
            val first = createHttpMonitor(httpMonitorRepository, monitorName = "First Monitor")
            val second = createHttpMonitor(httpMonitorRepository, monitorName = "Second Monitor")
            val window = createMaintenanceWindow(
                dslContext,
                name = "Scoped Window",
                global = false,
                monitors = listOf(first.monitorId(), second.monitorId()),
            )

            val page = newPage()
            val details = MaintenanceWindowDetailsPage(page)
            details.navigate(window.id)
            // Each affected monitor is a badge linking to that monitor's detail page.
            val affectedMonitors = details.detailRow(Messages.maintenanceWindowAffectedMonitors())
            assertThat(affectedMonitors.locator("a[href='/http-monitors/${first.id}']"))
                .containsText(first.monitorId().toString())
            assertThat(affectedMonitors.locator("a[href='/http-monitors/${second.id}']"))
                .containsText(second.monitorId().toString())
        }
    }
}
