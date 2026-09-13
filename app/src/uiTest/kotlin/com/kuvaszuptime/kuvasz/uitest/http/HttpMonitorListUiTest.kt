package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorListUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        // Verifies the HTMX auto-refresh: the list polls its fragment and picks up changes without a navigation.
        "the HTTP monitor list auto-refreshes to show a monitor added after the page loaded" {
            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Add a monitor straight into the DB *after* the page has rendered — no navigation, no manual refresh.
            createHttpMonitor(httpMonitorRepository, monitorName = "Auto Refreshed")

            // The list polls its fragment every 15s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "each row exposes clone, pause and delete action buttons" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Actions Monitor")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Monitor")
            assertThat(row.getByTestId("http-monitor-clone-button")).isVisible()
            assertThat(row.getByTestId("http-monitor-toggle-button")).isVisible()
            assertThat(row.getByTestId("http-monitor-delete-button")).isVisible()
        }

        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }

        "a monitor under an active maintenance window shows a grayed-out badge that keeps its status label" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Maintained HTTP")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createMaintenanceWindow(
                dslContext,
                name = "HTTP maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // The badge is grayed out (with a tool icon) but keeps the UP label
            assertThat(list.maintenanceBadge(monitor.name)).isVisible()
            assertThat(list.maintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
        }

        "the category filter offers the categories in use, plus the two catch-all options" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "search")
            createHttpMonitor(httpMonitorRepository, monitorName = "plain", category = null)

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // Case-insensitively ordered between the two fixed entries
            list.categoryOptions shouldBe listOf(
                Messages.allCategories(),
                "Payments",
                "search",
                Messages.uncategorizedMonitors(),
            )
            list.selectedCategory shouldBe Messages.allCategories()
        }

        "picking a category narrows the list and puts the choice into the URL" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")
            createHttpMonitor(httpMonitorRepository, monitorName = "plain", category = null)

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            list.names shouldBe listOf("pays", "plain", "searches")

            list.filterByCategory("Payments")

            // The choice travels in the URL, so the filtered list can be shared and bookmarked
            page.url() shouldContain "/http-monitors?category=Payments"
            assertThat(list.rowByName("pays")).isVisible()
            assertThat(list.rowByName("searches")).hasCount(0)
            assertThat(list.rowByName("plain")).hasCount(0)
        }

        "the uncategorized option lists only the monitors without a category" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "plain", category = null)

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            list.filterByCategory(Messages.uncategorizedMonitors())

            assertThat(list.rowByName("plain")).isVisible()
            assertThat(list.rowByName("pays")).hasCount(0)
        }

        "a category permalink opens the list already filtered, with the choice preselected" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigateToCategory("Payments")

            list.selectedCategory shouldBe "Payments"
            assertThat(list.rowByName("pays")).isVisible()
            assertThat(list.rowByName("searches")).hasCount(0)
        }

        "clearing the filter brings every monitor back" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigateToCategory("Payments")
            list.filterByCategory(Messages.allCategories())

            // Back to the plain URL, which is the canonical one for an unfiltered list
            page.url() shouldEndWith "/http-monitors"
            assertThat(list.rowByName("pays")).isVisible()
            assertThat(list.rowByName("searches")).isVisible()
        }

        // The list polls its own fragment, so the filter has to travel with every one of those requests too - not
        // only with the navigation that applied it.
        "the filter survives the HTMX auto-refresh of the list" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigateToCategory("Payments")
            assertThat(list.rowByName("pays")).isVisible()

            // Both are added after the page rendered, so only the next poll can bring them in
            createHttpMonitor(httpMonitorRepository, monitorName = "pays-too", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            assertThat(list.rowByName("pays-too"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
            // ...and the refreshed list is still scoped to the category, instead of falling back to everything
            assertThat(list.rowByName("searches")).hasCount(0)
        }

        "the HTTP monitor list is sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createHttpMonitor(httpMonitorRepository, monitorName = it) }

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            // The table is HTMX-swapped in, so wait for every row before reading their order.
            assertThat(list.rows).hasCount(names.size)
            list.names shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }
    }

    companion object {
        private const val AUTO_REFRESH_TIMEOUT_MS = 20_000.0
    }
}
