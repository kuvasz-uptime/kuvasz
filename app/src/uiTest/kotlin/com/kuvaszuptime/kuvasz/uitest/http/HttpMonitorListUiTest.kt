package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.MonitorListUiTestConfig
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.monitorListQueryTests
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
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

            // The list polls its fragment every 30s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "the rows flag the monitors checked through a proxy, and the ones whose proxy is not configured anymore" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Proxied Row", proxy = "corporate-egress")
            createHttpMonitor(httpMonitorRepository, monitorName = "Dangling Proxy Row", proxy = "removed-proxy")
            createHttpMonitor(httpMonitorRepository, monitorName = "Direct Row")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            with(list.rowByName("Proxied Row")) {
                assertThat(getByTestId("proxy-badge")).isVisible()
                assertThat(getByTestId("proxy-not-configured-badge")).hasCount(0)
            }
            with(list.rowByName("Dangling Proxy Row")) {
                assertThat(getByTestId("proxy-not-configured-badge")).isVisible()
                assertThat(getByTestId("proxy-badge")).hasCount(0)
            }
            with(list.rowByName("Direct Row")) {
                assertThat(getByTestId("proxy-badge")).hasCount(0)
                assertThat(getByTestId("proxy-not-configured-badge")).hasCount(0)
            }
        }

        "each row exposes configure, clone, pause and delete action buttons" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Actions Monitor")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Monitor")
            assertThat(list.configureButtonIn("Actions Monitor")).isVisible()
            // The view-only configuration button is reserved for the read-only monitors
            assertThat(list.configurationButtonIn("Actions Monitor")).hasCount(0)
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

        "a category that needs escaping in the URL is still filtered by, before and after a refresh" {
            createHttpMonitor(httpMonitorRepository, monitorName = "bills", category = "Payments & Billing")
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            list.filterByCategory("Payments & Billing")

            list.pagedList.expectQuery("/http-monitors", "category=Payments+%26+Billing")
            assertThat(list.rowByName("bills")).isVisible()
            assertThat(list.rowByName("searches")).hasCount(0)

            createHttpMonitor(httpMonitorRepository, monitorName = "bills-too", category = "Payments & Billing")
            list.refreshButton.click()

            assertThat(list.rowByName("bills-too")).isVisible()
            assertThat(list.rowByName("searches")).hasCount(0)

            // ...and the permalink opens it the same way
            page.reload()
            list.selectedCategory shouldBe "Payments & Billing"
            assertThat(list.rows).hasCount(2)
        }

        "a category without monitors is still offered when it's linked to, with an empty list" {
            createHttpMonitor(httpMonitorRepository, monitorName = "pays", category = "Payments")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigateToCategory("Search")

            assertThat(list.emptyState).containsText(Messages.noMatchingMonitors())
            assertThat(list.pagedList.pagination).hasCount(0)
            list.selectedCategory shouldBe "Search"
        }

        "the SSL filters narrow the list" {
            val sslStatuses = mapOf("valid" to SslStatus.VALID, "expiring" to SslStatus.WILL_EXPIRE)
            sslStatuses.forEach { (name, status) ->
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = name)
                createSSLEventRecord(dslContext, monitor.id, status, startedAt = OffsetDateTime.now(), endedAt = null)
            }
            createHttpMonitor(httpMonitorRepository, monitorName = "unchecked", sslCheckEnabled = false)

            val page = newPage()
            val list = HttpMonitorListPage(page)
            val filters = list.pagedList
            list.navigate()
            assertThat(list.rows).hasCount(sslStatuses.size + 1)
            filters.openFilters()

            filters.toggle("sslStatus", Messages.expiresSoon())
            filters.expectQuery("/http-monitors", "sslStatus=WILL_EXPIRE")
            assertThat(list.rows).hasCount(1)
            assertThat(list.rowByName("expiring")).isVisible()

            filters.toggle("sslStatus", Messages.valid())
            filters.expectQuery("/http-monitors", "sslStatus=VALID&sslStatus=WILL_EXPIRE")
            assertThat(list.rows).hasCount(2)

            filters.clearFiltersButton.click()
            filters.choose("sslCheckEnabled", Messages.no())
            filters.expectQuery("/http-monitors", "sslCheckEnabled=false")
            assertThat(list.rows).hasCount(1)
            assertThat(list.rowByName("unchecked")).isVisible()
        }

        // The list polls its own fragment, so its state has to travel with every one of those requests too - not
        // only with the change that applied it
        "the filters, the sorting and the page survive the HTMX auto-refresh of the list" {
            for (index in 1..ONE_MORE_THAN_A_PAGE) {
                val name = "pays-${"$index".padStart(2, '0')}"
                createHttpMonitor(httpMonitorRepository, monitorName = name, category = "Payments")
            }
            createHttpMonitor(httpMonitorRepository, monitorName = "searches", category = "Search")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            page.navigate("/http-monitors?category=Payments&sortedBy=NAME&sortOrder=DESC&page=2")
            assertThat(list.rows).hasCount(1)
            assertThat(list.rowByName("pays-01")).isVisible()

            // Added after the page rendered, so only the next poll can bring it in, onto the second page
            createHttpMonitor(httpMonitorRepository, monitorName = "pays-00", category = "Payments")

            assertThat(list.rowByName("pays-00"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
            assertThat(list.rows).hasCount(2)
            assertThat(list.rowByName("searches")).hasCount(0)
            list.pagedList.expectQuery("/http-monitors", "category=Payments&sortedBy=NAME&sortOrder=DESC&page=2")
        }

        "deleting the only monitor of the last page goes back to the page before it" {
            for (index in 1..ONE_MORE_THAN_A_PAGE) {
                createHttpMonitor(httpMonitorRepository, monitorName = "Monitor ${"$index".padStart(2, '0')}")
            }

            val page = newPage()
            val list = HttpMonitorListPage(page)
            page.navigate("/http-monitors?page=2")
            assertThat(list.rows).hasCount(1)

            list.deleteMonitor("Monitor $ONE_MORE_THAN_A_PAGE")

            assertThat(list.rows).hasCount(PageRequest.DEFAULT_SIZE)
            list.pagedList.expectQuery("/http-monitors")
            assertThat(list.pagedList.paginationSummary)
                .hasText(Messages.listPaginationSummary(1, PageRequest.DEFAULT_SIZE, PageRequest.DEFAULT_SIZE))
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

        monitorListQueryTests(
            MonitorListUiTestConfig(
                slug = "http",
                createMonitor = { name, category, enabled ->
                    createHttpMonitor(httpMonitorRepository, monitorName = name, category = category, enabled = enabled)
                },
                startUptimeStatus = { name, status, startedAt ->
                    val monitorId = checkNotNull(httpMonitorRepository.findByName(name)).id
                    createHttpUptimeEventRecord(dslContext, monitorId, status, startedAt, endedAt = null)
                },
                otherSortFields = listOf("SSL_STATUS"),
            )
        )
    }

    companion object {
        private const val AUTO_REFRESH_TIMEOUT_MS = 35_000.0
        private const val ONE_MORE_THAN_A_PAGE = PageRequest.DEFAULT_SIZE + 1
    }
}
