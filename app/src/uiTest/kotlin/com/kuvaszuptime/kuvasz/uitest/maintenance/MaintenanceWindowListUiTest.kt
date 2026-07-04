package com.kuvaszuptime.kuvasz.uitest.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowListUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a maintenance window can be enabled and disabled from the list" {
            createMaintenanceWindow(dslContext, name = "List Toggle Window", enabled = true)

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            assertThat(list.rowByName("List Toggle Window")).containsText(Messages.maintenanceWindowActive())

            list.toggle("List Toggle Window")
            assertThat(list.rowByName("List Toggle Window")).containsText(Messages.disabled())

            list.toggle("List Toggle Window")
            assertThat(list.rowByName("List Toggle Window")).containsText(Messages.maintenanceWindowActive())
        }

        "a global window shows the all-monitors badge in the monitors column" {
            createMaintenanceWindow(dslContext, name = "Global Window", global = true)

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            // The monitors column carries the global-scope badge rather than a count.
            assertThat(list.monitorsCell("Global Window")).containsText(Messages.maintenanceWindowGlobalScope())
        }

        "a monitor-scoped window shows its affected-monitor count in the monitors column" {
            val first = createHttpMonitor(httpMonitorRepository, monitorName = "First Monitor")
            val second = createHttpMonitor(httpMonitorRepository, monitorName = "Second Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "Scoped Window",
                global = false,
                monitors = listOf(first.monitorId(), second.monitorId()),
            )

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            // The monitors column shows the affected-monitor count, not the global badge.
            assertThat(list.monitorsCell("Scoped Window")).hasText("2")
        }
    }
}
