package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.regex.Pattern

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DashboardUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : UiTestSpec() {
    init {
        "the dashboard shows a placeholder and no verdict when there is no monitor at all" {
            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.emptyState).isVisible()
            assertThat(dashboard.heading).hasText("Monitoring")
            assertThat(dashboard.statusIndicator).not().isAttached()
            assertThat(dashboard.monitorTypesCard).not().isAttached()
        }

        "the dashboard swaps an all-clear verdict into the header when every monitor is fine" {
            createHealthyMonitor()

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.heading).hasText("All systems operational")
            assertThat(dashboard.statusIndicator).isVisible()
            assertThat(dashboard.statusDetails.getByLabel("Up: 1")).hasText("1")
            assertThat(dashboard.uptimeCard).isVisible()
            assertThat(dashboard.recentIncidentsCard).containsText("No incidents in the last 7 days")
            assertThat(dashboard.leastReliableMonitorsCard).containsText("No monitor was down in the last 7 days")
            assertThat(dashboard.emptyState).not().isAttached()
        }

        "the buttons of the header are next to the verdict on big screens, and below it on small ones" {
            createHealthyMonitor()

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()
            assertThat(dashboard.heading).hasText("All systems operational")

            // The default viewport is a big screen
            assertThat(dashboard.periodSelector).isInViewport()
            dashboard.periodSelector.top() shouldBeLessThan dashboard.heading.bottom()
            // The period is picked between creating a monitor and refreshing the dashboard
            dashboard.createMonitorButton.left() shouldBeLessThan dashboard.periodSelector.left()
            dashboard.periodSelector.left() shouldBeLessThan dashboard.refreshButton.left()

            page.setViewportSize(SMALL_SCREEN_WIDTH, SMALL_SCREEN_HEIGHT)

            // Nothing is hidden, the buttons just get a row of their own below the verdict and its details
            assertThat(dashboard.heading).isVisible()
            assertThat(dashboard.statusDetails).isVisible()
            dashboard.periodSelector.top() shouldBeGreaterThan dashboard.statusDetails.bottom()
            // ...starting at the left end of the page
            dashboard.createMonitorButton.left() shouldBeLessThan MAX_LEFT_GUTTER
        }

        "the create modals of the different monitor types don't share the ids of their fields" {
            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            // Every type's create modal is on the page at once, so the browser couldn't tell their fields apart
            val duplicatedIds = page.evaluate(
                """
                () => {
                    const ids = [...document.querySelectorAll('[id]')].map(element => element.id);
                    return [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
                }
                """.trimIndent()
            )
            duplicatedIds shouldBe emptyList<String>()
        }

        "the dashboard only lists the monitor types that have monitors" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Dashboard HTTP Monitor")
            createDnsMonitor(dnsMonitorRepository, monitorName = "Dashboard DNS Monitor")

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.monitorTypeRow("http")).isVisible()
            assertThat(dashboard.monitorTypeRow("dns")).isVisible()
            listOf("push", "icmp", "tcp", "docker").forEach { type ->
                assertThat(dashboard.monitorTypeRow(type)).not().isAttached()
            }
        }

        "the dashboard calls out the monitors that are down and lists both the ongoing and the resolved incidents" {
            val now = getCurrentTimestamp()
            val down = createHttpMonitor(httpMonitorRepository, monitorName = "Down Monitor", sslCheckEnabled = false)
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = down.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusMinutes(1),
                endedAt = null,
                error = "Connection refused",
            )
            val flaky = createHttpMonitor(httpMonitorRepository, monitorName = "Flaky Monitor", sslCheckEnabled = false)
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = flaky.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(2),
                endedAt = now.minusHours(1),
            )

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.heading).hasText("1 of 2 monitors down")
            // The counts of the type are icons, labelled by their tooltips only
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Down: 1")).hasText("1")
            assertThat(dashboard.statusDotOf(dashboard.incident("Down Monitor"))).hasAttribute("aria-label", "Ongoing")
            assertThat(dashboard.incident("Down Monitor")).containsText("started")
            // The type is an icon, named by its tooltip only
            assertThat(dashboard.incident("Down Monitor").getByLabel("HTTP")).isVisible()
            assertThat(dashboard.statusDotOf(dashboard.incident("Flaky Monitor")))
                .hasAttribute("aria-label", "Resolved")
            assertThat(dashboard.incident("Flaky Monitor")).containsText("resolved")
            // The ongoing incidents come first
            assertThat(dashboard.incidents.first()).containsText("Down Monitor")
            assertThat(dashboard.incidentsCountCard).containsText("Ongoing: 1")
            assertThat(dashboard.incidentsCountCard).containsText("Affected monitors: 2")
            // The one that was down longer comes first, even if it's not down anymore
            assertThat(dashboard.leastReliableMonitors).hasCount(2)
            assertThat(dashboard.leastReliableMonitors.first()).containsText("Flaky Monitor")
            // The figures are icons, labelled by their tooltips only
            assertThat(dashboard.leastReliableMonitors.first().getByLabel("Downtime: 1 hour")).hasText("1 hour")
            assertThat(dashboard.leastReliableMonitors.first().getByLabel("Incidents: 1")).hasText("1")
            val downMonitor = dashboard.leastReliableMonitors.last()
            assertThat(downMonitor).containsText("Down Monitor")
            assertThat(downMonitor.getByLabel("HTTP & SSL")).isVisible()
            assertThat(dashboard.statusDotOf(downMonitor)).hasAttribute("aria-label", "Down")
            assertThat(dashboard.statusDotOf(downMonitor)).hasClass(Pattern.compile("status-red"))
            assertThat(downMonitor.getByRole(AriaRole.LINK)).hasAttribute("href", "/http-monitors/${down.id}")
        }

        "the dashboard lists the certificates that are about to expire" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Expiring Certificate")
            createSSLEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = SslStatus.WILL_EXPIRE,
                startedAt = getCurrentTimestamp().minusDays(1),
                endedAt = null,
                sslExpiryDate = getCurrentTimestamp().plusDays(2),
            )

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.heading).hasText("Certificates expiring soon")
            assertThat(dashboard.certificates).hasCount(1)
            assertThat(dashboard.certificates.first()).containsText("Expiring Certificate")
            assertThat(dashboard.certificatesCard.getByLabel("Valid: 0")).hasText("0")
            assertThat(dashboard.certificatesCard.getByLabel("Expires soon: 1")).hasText("1")
            // Only the counts that aren't zero are shown, except for the valid ones
            assertThat(dashboard.certificatesCard.getByLabel("Invalid: 0")).not().isAttached()
        }

        "the dashboard doesn't show a certificates card without a monitor that checks one" {
            createHttpMonitor(httpMonitorRepository, monitorName = "No SSL Monitor", sslCheckEnabled = false)

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.monitorTypesCard).isVisible()
            assertThat(dashboard.certificatesCard).not().isAttached()
        }

        "the dashboard lists the active and the upcoming maintenance windows, the active ones first" {
            createHttpMonitor(httpMonitorRepository, sslCheckEnabled = false)
            createMaintenanceWindow(
                dslContext,
                name = "Weekly patching",
                start = getCurrentTimestamp().plusDays(1),
                duration = "PT1H",
            )
            createMaintenanceWindow(
                dslContext,
                name = "Database upgrade",
                start = getCurrentTimestamp().minusHours(1),
                duration = "PT2H",
            )

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.maintenanceWindows).hasCount(2)
            // The same colors as on the list of the maintenance windows: green if active, yellow if scheduled
            val active = dashboard.maintenanceWindows.first()
            assertThat(active).containsText("Database upgrade")
            assertThat(active).containsText("ends in")
            assertThat(dashboard.statusDotOf(active)).hasAttribute("aria-label", "Active")
            assertThat(dashboard.statusDotOf(active)).hasClass(Pattern.compile("status-green"))
            val scheduled = dashboard.maintenanceWindows.last()
            assertThat(scheduled).containsText("Weekly patching")
            assertThat(scheduled).containsText("starts in")
            assertThat(dashboard.statusDotOf(scheduled)).hasAttribute("aria-label", "Scheduled")
            assertThat(dashboard.statusDotOf(scheduled)).hasClass(Pattern.compile("status-yellow"))
            assertThat(dashboard.manageMaintenanceWindowsLink).hasAttribute("href", "/maintenance-windows")
            // The heading tells how far ahead the upcoming windows are looked for
            assertThat(dashboard.maintenanceCard.getByTestId("dashboard-card-subtitle")).hasText("Next 7 days")
        }

        "picking another period reloads the dashboard with the overview of that period" {
            createHttpMonitor(httpMonitorRepository, sslCheckEnabled = false)

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()
            assertThat(dashboard.monitorTypesCard).isVisible()

            dashboard.selectPeriod("PT720H")

            assertThat(page).hasURL(Pattern.compile(".*/\\?period=PT720H$"))
            assertThat(dashboard.periodSelector).hasValue("PT720H")
            assertThat(dashboard.viewAllIncidentsLink).hasAttribute("href", "/incidents?period=PT720H")
            assertThat(dashboard.recentIncidentsCard.getByTestId("dashboard-card-subtitle")).hasText("Last 30 days")
        }

        "refreshing the dashboard replaces the placeholder with the overview of the freshly created monitor" {
            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()
            assertThat(dashboard.emptyState).isVisible()

            // Added straight into the DB *after* the page has rendered — no navigation, no manual page reload.
            createHttpMonitor(httpMonitorRepository, monitorName = "Refreshed Monitor", sslCheckEnabled = false)
            dashboard.refresh()

            assertThat(dashboard.emptyState).not().isAttached()
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Pending: 1")).hasText("1")
            // The verdict in the page header is refreshed together with the overview
            assertThat(dashboard.heading).hasText("Waiting for the first checks")
        }
    }

    private fun Locator.top(): Double = boundingBox().shouldNotBeNull().y

    private fun Locator.bottom(): Double = boundingBox().shouldNotBeNull().let { it.y + it.height }

    private fun Locator.left(): Double = boundingBox().shouldNotBeNull().x

    private fun createHealthyMonitor() {
        val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Healthy Monitor", sslCheckEnabled = false)
        createHttpUptimeEventRecord(
            dslContext,
            monitorId = monitor.id,
            status = UptimeStatus.UP,
            startedAt = getCurrentTimestamp().minusHours(1),
            endedAt = null,
        )
    }

    companion object {
        private const val SMALL_SCREEN_WIDTH = 390
        private const val SMALL_SCREEN_HEIGHT = 844

        // The padding of the page and of its grid columns, between the first button and the edge of the screen
        private const val MAX_LEFT_GUTTER = 32.0
    }
}
