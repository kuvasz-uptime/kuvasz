package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorListUiTest(private val icmpMonitorRepository: IcmpMonitorRepository) : UiTestSpec() {
    init {
        // Verifies the HTMX auto-refresh: the list polls its fragment and picks up changes without a navigation.
        "the ICMP monitor list auto-refreshes to show a monitor added after the page loaded" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Add a monitor straight into the DB *after* the page has rendered — no navigation, no manual refresh.
            createIcmpMonitor(icmpMonitorRepository, monitorName = "Auto Refreshed")

            // The list polls its fragment every 15s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "each row exposes clone, pause and delete action buttons" {
            createIcmpMonitor(icmpMonitorRepository, monitorName = "Actions Monitor")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Monitor")
            assertThat(row.getByTestId("icmp-monitor-clone-button")).isVisible()
            assertThat(row.getByTestId("icmp-monitor-toggle-button")).isVisible()
            assertThat(row.getByTestId("icmp-monitor-delete-button")).isVisible()
        }

        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createIcmpMonitor(icmpMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }

        "a monitor under an active maintenance window shows a grayed-out badge that keeps its status label" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "Maintained ICMP")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createIcmpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createMaintenanceWindow(
                dslContext,
                name = "ICMP maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name)),
            )

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()

            // The badge is grayed out (with a tool icon) but keeps the UP label
            assertThat(list.maintenanceBadge(monitor.name)).isVisible()
            assertThat(list.maintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
        }

        "the ICMP monitor list is sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createIcmpMonitor(icmpMonitorRepository, monitorName = it) }

            val page = newPage()
            val list = IcmpMonitorListPage(page)
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
