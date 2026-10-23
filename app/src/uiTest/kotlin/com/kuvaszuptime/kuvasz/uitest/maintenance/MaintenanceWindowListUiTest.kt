package com.kuvaszuptime.kuvasz.uitest.maintenance

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.maintenance.MaintenanceWindowListPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class MaintenanceWindowListUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a maintenance window can be enabled and disabled from the list" {
            createMaintenanceWindow(dslContext, name = "List Toggle Window", enabled = true)

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            assertThat(list.statusBadge("List Toggle Window", Messages.maintenanceWindowActive())).isVisible()

            list.toggle("List Toggle Window")
            assertThat(list.statusBadge("List Toggle Window", Messages.disabled())).isVisible()

            list.toggle("List Toggle Window")
            assertThat(list.statusBadge("List Toggle Window", Messages.maintenanceWindowActive())).isVisible()
        }

        "each row exposes configure, clone, toggle and delete action buttons" {
            createMaintenanceWindow(dslContext, name = "Actions Window")

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Window")
            assertThat(list.configureButtonIn("Actions Window")).isVisible()
            // The view-only configuration button is reserved for the read-only maintenance windows
            assertThat(list.configurationButtonIn("Actions Window")).hasCount(0)
            assertThat(row.getByTestId("maintenance-window-clone-button")).isVisible()
            assertThat(row.getByTestId("maintenance-window-toggle-button")).isVisible()
            assertThat(row.getByTestId("maintenance-window-delete-button")).isVisible()
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

        "a category-scoped window shows its category count in the categories column" {
            createMaintenanceWindow(
                dslContext,
                name = "Category Window",
                global = false,
                categories = listOf("Payments", "Search"),
            )

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            assertThat(list.categoriesCell("Category Window")).hasText("2")
        }

        "a global window shows a placeholder in the categories column" {
            createMaintenanceWindow(dslContext, name = "Global Window", global = true, categories = listOf("Ignored"))

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            // A global window covers every monitor, so its selectors are irrelevant
            assertThat(list.categoriesCell("Global Window")).hasText("-")
        }

        "the list shows a placeholder instead of an empty table when there is no maintenance window" {
            val page = newPage()
            val list = MaintenanceWindowListPage(page)

            list.navigate()

            assertThat(list.emptyState).isVisible()
            assertThat(list.emptyState).containsText(Messages.noMaintenanceWindowsYet())
            assertThat(list.rows).hasCount(0)
        }

        "the maintenance window list is sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createMaintenanceWindow(dslContext, name = it) }

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()

            // The table is HTMX-swapped in, so wait for every row before reading their order.
            assertThat(list.rows).hasCount(names.size)
            list.names shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }

        "the maintenance windows can be searched by their name and filtered by whether they are enabled" {
            createMaintenanceWindow(dslContext, name = "Database upgrade", enabled = true)
            createMaintenanceWindow(dslContext, name = "Database backup", enabled = false)
            createMaintenanceWindow(dslContext, name = "Network", enabled = true)

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            val filters = list.pagedList
            list.navigate()
            assertThat(list.rows).hasCount(FILTERED_WINDOW_COUNT)

            filters.openFilters()
            filters.searchByName("database")
            filters.expectQuery("/maintenance-windows", "name=database")
            assertThat(list.rows).hasCount(2)

            filters.choose("enabled", Messages.no())
            filters.expectQuery("/maintenance-windows", "name=database&enabled=false")
            assertThat(list.rows).hasCount(1)
            assertThat(list.rowByName("Database backup")).isVisible()

            filters.searchByName("nothing like this")
            assertThat(list.emptyState).containsText(Messages.noMatchingMaintenanceWindows())

            filters.clearFiltersButton.click()
            filters.expectQuery("/maintenance-windows")
            assertThat(list.rows).hasCount(FILTERED_WINDOW_COUNT)
        }

        "the maintenance windows are sorted by clicking the headers" {
            val now = OffsetDateTime.now()
            createMaintenanceWindow(dslContext, name = "Later", start = now.plusDays(2), duration = "PT1H")
            createMaintenanceWindow(dslContext, name = "Sooner", start = now.plusDays(1), duration = "PT1H")
            createMaintenanceWindow(dslContext, name = "Manual", enabled = false)

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            val sorting = list.pagedList
            val names = list.rows.locator("td:first-of-type")
            list.navigate()
            assertThat(names).hasText(arrayOf("Later", "Manual", "Sooner"))

            // The windows without a fixed start come last, whatever the order is
            sorting.sortHeader("START").click()
            sorting.expectQuery("/maintenance-windows", "sortedBy=START")
            assertThat(names).hasText(arrayOf("Sooner", "Later", "Manual"))

            sorting.sortHeader("START").click()
            sorting.expectQuery("/maintenance-windows", "sortedBy=START&sortOrder=DESC")
            assertThat(names).hasText(arrayOf("Later", "Sooner", "Manual"))

            sorting.sortHeader("ENABLED").click()
            sorting.expectQuery("/maintenance-windows", "sortedBy=ENABLED")
            assertThat(names.first()).hasText("Manual")

            sorting.sortHeader("ID").click()
            sorting.expectQuery("/maintenance-windows", "sortedBy=ID")
            assertThat(names).hasText(arrayOf("Later", "Sooner", "Manual"))
        }

        "the maintenance windows are paged" {
            for (index in 1..WINDOW_COUNT) {
                createMaintenanceWindow(dslContext, name = "Window ${"$index".padStart(2, '0')}")
            }

            val page = newPage()
            val list = MaintenanceWindowListPage(page)
            list.navigate()
            assertThat(list.rows).hasCount(PageRequest.DEFAULT_SIZE)

            list.pagedList.pageLink(2).click()
            list.pagedList.expectQuery("/maintenance-windows", "page=2")
            assertThat(list.rows).hasCount(WINDOW_COUNT - PageRequest.DEFAULT_SIZE)
        }
    }

    private companion object {
        const val WINDOW_COUNT = PageRequest.DEFAULT_SIZE + 2
        const val FILTERED_WINDOW_COUNT = 3
    }
}
