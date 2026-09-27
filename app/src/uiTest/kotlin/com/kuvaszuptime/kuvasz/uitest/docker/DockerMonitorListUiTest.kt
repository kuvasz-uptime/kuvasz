package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerMonitorListUiTest(private val dockerMonitorRepository: DockerMonitorRepository) : UiTestSpec() {
    init {
        // Verifies the HTMX auto-refresh: the list polls its fragment and picks up changes without a navigation.
        "the Docker monitor list auto-refreshes to show a monitor added after the page loaded" {
            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Add a monitor straight into the DB *after* the page has rendered — no navigation, no manual refresh.
            createDockerMonitor(dockerMonitorRepository, monitorName = "Auto Refreshed")

            // The list polls its fragment every 15s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "each row exposes configure, clone, pause and delete action buttons" {
            createDockerMonitor(dockerMonitorRepository, monitorName = "Actions Monitor")

            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            val row = list.rowByName("Actions Monitor")
            assertThat(list.configureButtonIn("Actions Monitor")).isVisible()
            // The view-only configuration button is reserved for the read-only monitors
            assertThat(list.configurationButtonIn("Actions Monitor")).hasCount(0)
            assertThat(row.getByTestId("docker-monitor-clone-button")).isVisible()
            assertThat(row.getByTestId("docker-monitor-toggle-button")).isVisible()
            assertThat(row.getByTestId("docker-monitor-delete-button")).isVisible()
        }

        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createDockerMonitor(dockerMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }

        "a monitor under an active maintenance window shows a grayed-out badge that keeps its status label" {
            val monitor = createDockerMonitor(dockerMonitorRepository, monitorName = "Maintained Docker")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createDockerUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createMaintenanceWindow(
                dslContext,
                name = "Docker maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name)),
            )

            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            // The badge is grayed out (with a tool icon) but keeps the UP label
            assertThat(list.maintenanceBadge(monitor.name)).isVisible()
            assertThat(list.maintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
        }

        "a monitor whose Docker host is not configured anymore is flagged with a badge in its row" {
            createDockerMonitor(dockerMonitorRepository, monitorName = "Dangling Docker", dockerHost = "removed-host")
            createDockerMonitor(dockerMonitorRepository, monitorName = "Configured Docker", dockerHost = "local")

            val page = newPage()
            val list = DockerMonitorListPage(page)
            list.navigate()

            assertThat(list.danglingHostBadge("Dangling Docker")).isVisible()
            // Label-less, so it is the tooltip that says what is wrong
            list.danglingHostBadgeTooltip("Dangling Docker") shouldBe
                Messages.dockerHostNotConfiguredTooltip("removed-host")
            assertThat(list.danglingHostBadge("Configured Docker")).hasCount(0)
        }

        "the Docker monitor list is sorted by name, regardless of its casing" {
            val names = listOf("Charlie", "bravo", "Delta", "alpha")
            names.forEach { createDockerMonitor(dockerMonitorRepository, monitorName = it) }

            val page = newPage()
            val list = DockerMonitorListPage(page)
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
