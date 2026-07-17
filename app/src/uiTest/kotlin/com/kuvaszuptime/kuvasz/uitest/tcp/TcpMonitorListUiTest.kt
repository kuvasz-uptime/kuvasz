package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class TcpMonitorListUiTest(private val tcpMonitorRepository: TcpMonitorRepository) : UiTestSpec() {
    init {
        // Verifies the HTMX auto-refresh: the list polls its fragment and picks up changes without a navigation.
        "the TCP monitor list auto-refreshes to show a monitor added after the page loaded" {
            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Add a monitor straight into the DB *after* the page has rendered — no navigation, no manual refresh.
            createTcpMonitor(tcpMonitorRepository, monitorName = "Auto Refreshed")

            // The list polls its fragment every 15s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "each row exposes clone, pause and delete action buttons" {
            createTcpMonitor(tcpMonitorRepository, monitorName = "Actions Monitor")

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Monitor")
            assertThat(row.getByTestId("tcp-monitor-clone-button")).isVisible()
            assertThat(row.getByTestId("tcp-monitor-toggle-button")).isVisible()
            assertThat(row.getByTestId("tcp-monitor-delete-button")).isVisible()
        }

        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createTcpMonitor(tcpMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }

        "a monitor under an active maintenance window shows a grayed-out badge that keeps its status label" {
            val monitor = createTcpMonitor(tcpMonitorRepository, monitorName = "Maintained TCP")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createTcpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createMaintenanceWindow(
                dslContext,
                name = "TCP maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.TCP, monitor.name)),
            )

            val page = newPage()
            val list = TcpMonitorListPage(page)
            list.navigate()

            // The badge is grayed out (with a tool icon) but keeps the UP label
            assertThat(list.maintenanceBadge(monitor.name)).isVisible()
            assertThat(list.maintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
        }
    }

    companion object {
        private const val AUTO_REFRESH_TIMEOUT_MS = 20_000.0
    }
}
