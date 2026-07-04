package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PublicStatusPageUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a public status page is reachable without authentication and shows its monitor" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Public API")
            val pageTitle = "Public Systems Status"
            val slug = "public-systems"
            createStatusPage(
                dslContext,
                title = pageTitle,
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.title(pageTitle).first()).isVisible()
            assertThat(statusPage.monitorCard(monitor.name)).isVisible()
        }

        "a monitor that is currently down is rendered with a DOWN status" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Down Service")
            // An ongoing (endedAt = null) DOWN event makes the monitor's current status DOWN.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createStatusPage(
                dslContext,
                title = "Down Status",
                slug = "down-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("down-status")

            assertThat(statusPage.monitorCardBody("Down Service")).containsText(UptimeStatus.DOWN.literal)
        }

        "a monitor under an active maintenance window is shown with the banner and a grayed-out badge" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Maintained API")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            val slug = "maintenance-status"
            createStatusPage(
                dslContext,
                title = "Maintenance Status",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )
            // A manual (unscheduled) enabled window is permanently active for its monitors.
            createMaintenanceWindow(
                dslContext,
                name = "Database upgrade",
                description = "Rolling out a new database version",
                enabled = true,
                showOnStatusPages = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // The active maintenance banner lists the window with its name and description
            assertThat(statusPage.maintenanceBanner()).isVisible()
            assertThat(statusPage.maintenanceBanner()).containsText("Database upgrade")
            assertThat(statusPage.maintenanceBanner()).containsText("Rolling out a new database version")
            // The monitor's badge is grayed out but keeps its UP label
            assertThat(statusPage.monitorMaintenanceBadge(monitor.name)).isVisible()
            assertThat(statusPage.monitorMaintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
            // A manual window has no scheduled timeframe, so no timeframe pill is rendered
            assertThat(statusPage.maintenanceTimeframe()).hasCount(0)
        }

        "an active scheduled maintenance window renders its timeframe as a start-end pill" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Scheduled API")
            val slug = "scheduled-maintenance-status"
            createStatusPage(
                dslContext,
                title = "Scheduled Maintenance Status",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )
            // A single (one-shot) window that started an hour ago and lasts two hours is active right now, so it has a
            // concrete [start, end) interval that must be rendered as a timeframe pill.
            val windowStart = OffsetDateTime.now().minusHours(1)
            createMaintenanceWindow(
                dslContext,
                name = "Scheduled DB maintenance",
                enabled = true,
                showOnStatusPages = true,
                start = windowStart,
                duration = "PT2H",
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.maintenanceBanner()).containsText("Scheduled DB maintenance")
            // The timeframe pill is rendered with the window's start and end timestamps
            assertThat(statusPage.maintenanceTimeframe()).isVisible()
            assertThat(statusPage.maintenanceTimeframe()).containsText(windowStart.year.toString())
        }
    }
}
