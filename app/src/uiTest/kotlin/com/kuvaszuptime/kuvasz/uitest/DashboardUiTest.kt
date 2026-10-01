package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
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
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-green"))
            assertThat(dashboard.statusDetails.getByLabel("Up: 1")).hasText("1")
            // The same figures every time, even the zeros
            listOf("Down: 0", "Maintenance: 0", "Paused: 0", "Pending: 0").forEach { label ->
                assertThat(dashboard.statusDetails.getByLabel(label)).hasText("0")
            }
            assertThat(dashboard.statusDetails.getByLabel("No incidents")).hasText("-")
            assertThat(dashboard.uptimeCard).isVisible()
            assertThat(dashboard.recentIncidentsCard).containsText("No incidents in this period")
            assertThat(dashboard.leastReliableMonitorsCard).containsText("No monitor was down in this period")
            assertThat(dashboard.maintenanceCard).containsText("No maintenance in the next 7 days")
            // Without any incident, there is nothing to resolve or to chart
            assertThat(dashboard.metricValueOf(dashboard.incidentsCountCard)).hasText("0")
            assertThat(dashboard.metricValueOf(dashboard.downtimeCard)).hasText("-")
            assertThat(dashboard.metricValueOf(dashboard.meanTimeToResolveCard)).hasText("-")
            assertThat(dashboard.meanTimeToResolveCard).containsText("Resolved: 0")
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

            assertThat(dashboard.heading).hasText("Monitors down: 1 of 2")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-red"))
            assertThat(dashboard.statusDetails.getByLabel("Down: 1")).hasClass(Pattern.compile("text-red"))
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Down: 1")).hasText("1")
            assertThat(dashboard.statusDotOf(dashboard.incident("Down Monitor"))).hasAttribute("aria-label", "Ongoing")
            assertThat(dashboard.incident("Down Monitor")).containsText("started")
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
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-yellow"))
            assertThat(dashboard.certificates).hasCount(1)
            assertThat(dashboard.certificates.first()).containsText("Expiring Certificate")
            assertThat(dashboard.moreCertificatesLink).not().isAttached()
            assertThat(dashboard.certificatesCard.getByLabel("Valid: 0")).hasText("0")
            assertThat(dashboard.certificatesCard.getByLabel("Expires soon: 1")).hasText("1")
            // Only the counts that aren't zero are shown, except for the valid ones
            assertThat(dashboard.certificatesCard.getByLabel("Invalid: 0")).not().isAttached()
        }

        "the dashboard lists only the soonest expiring certificates, but calls out the rest of them" {
            val now = getCurrentTimestamp()
            repeat(MORE_THAN_THE_LISTED_CERTIFICATES) { index ->
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Certificate $index")
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = SslStatus.WILL_EXPIRE,
                    startedAt = now.minusDays(1),
                    endedAt = null,
                    sslExpiryDate = now.plusDays(index + 1L),
                )
            }

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.certificates).hasCount(LISTED_CERTIFICATES)
            assertThat(dashboard.certificates.first()).containsText("Certificate 0")
            assertThat(dashboard.certificatesCard.getByLabel("Expires soon: $MORE_THAN_THE_LISTED_CERTIFICATES"))
                .hasText("$MORE_THAN_THE_LISTED_CERTIFICATES")
            assertThat(dashboard.moreCertificatesLink)
                .hasText("More certificates with issues: ${MORE_THAN_THE_LISTED_CERTIFICATES - LISTED_CERTIFICATES}")
            assertThat(dashboard.moreCertificatesLink).hasAttribute("href", "/http-monitors")
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
            assertThat(dashboard.moreMaintenanceWindowsLink).not().isAttached()
        }

        "the dashboard lists only the first maintenance windows, but calls out the rest of them" {
            val now = getCurrentTimestamp()
            createHttpMonitor(httpMonitorRepository, sslCheckEnabled = false)
            repeat(MORE_THAN_THE_LISTED_MAINTENANCE_WINDOWS) { index ->
                createMaintenanceWindow(
                    dslContext,
                    name = "Window $index",
                    start = now.plusHours(index + 1L),
                    duration = "PT1H",
                )
            }

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.maintenanceWindows).hasCount(LISTED_MAINTENANCE_WINDOWS)
            assertThat(dashboard.maintenanceWindows.first()).containsText("Window 0")
            assertThat(dashboard.moreMaintenanceWindowsLink).hasText(
                "More maintenance windows: ${MORE_THAN_THE_LISTED_MAINTENANCE_WINDOWS - LISTED_MAINTENANCE_WINDOWS}"
            )
            assertThat(dashboard.moreMaintenanceWindowsLink).hasAttribute("href", "/maintenance-windows")
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

        "the dashboard only mentions the monitors that are down under maintenance, without any alarm" {
            val now = getCurrentTimestamp()
            createHealthyMonitor()
            val maintained =
                createHttpMonitor(httpMonitorRepository, monitorName = "Maintained", sslCheckEnabled = false)
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = maintained.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            val maintainedUp =
                createHttpMonitor(httpMonitorRepository, monitorName = "Maintained up", sslCheckEnabled = false)
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = maintainedUp.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            createMaintenanceWindow(
                dslContext,
                monitors = listOf(maintained, maintainedUp).map { MonitorID(MonitorType.HTTP_SSL, it.name) },
            )

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.heading).hasText("Down during maintenance: 1")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-gray"))
            // The monitors under maintenance are only counted by the maintenance, no matter whether they're up or down
            assertThat(dashboard.statusDetails.getByLabel("Maintenance: 2")).hasText("2")
            assertThat(dashboard.statusDetails.getByLabel("Up: 1")).hasText("1")
            assertThat(dashboard.statusDetails.getByLabel("Down: 0")).hasText("0")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Maintenance: 2")).hasText("2")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Up: 1")).hasText("1")
            // The incident is listed with the same gray dot as everywhere else, and it isn't counted in red either
            val incidentDot = dashboard.statusDotOf(dashboard.incident("Maintained"))
            assertThat(incidentDot).hasAttribute("aria-label", "Maintenance")
            assertThat(incidentDot).hasClass(Pattern.compile("status-gray"))
            assertThat(dashboard.incidentsCountCard).containsText("In maintenance: 1")
            assertThat(dashboard.incidentsCountCard).not().containsText("Ongoing")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Down: 1")).not().isAttached()
            assertThat(dashboard.statusDotOf(dashboard.leastReliableMonitors.first()))
                .hasClass(Pattern.compile("status-gray"))
        }

        "the dashboard only mentions the maintenance when every monitor that isn't paused is under one" {
            createHealthyMonitor()
            createMaintenanceWindow(dslContext, global = true)

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.heading).hasText("In maintenance: 1")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-gray"))
            // The monitor is up, but it's only counted by the maintenance
            assertThat(dashboard.statusDetails.getByLabel("Up: 0")).hasText("0")
            assertThat(dashboard.statusDetails.getByLabel("Maintenance: 1")).hasText("1")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Up: 0")).hasText("0")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Maintenance: 1")).hasText("1")
        }

        "the dashboard tells when every monitor is paused, and leaves their incidents out" {
            val now = getCurrentTimestamp()
            val paused = createHttpMonitor(
                httpMonitorRepository,
                monitorName = "Paused Monitor",
                sslCheckEnabled = false,
                enabled = false,
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = paused.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(OUTAGE_STARTED_HOURS_AGO),
                endedAt = now.minusHours(2),
            )

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.heading).hasText("Every monitor is paused")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-cyan"))
            assertThat(dashboard.statusDetails.getByLabel("Paused: 1")).hasText("1")
            assertThat(dashboard.monitorTypeRow("http").getByLabel("Paused: 1")).hasText("1")
            // A paused monitor isn't checked, so none of its incidents are counted or listed
            assertThat(dashboard.metricValueOf(dashboard.incidentsCountCard)).hasText("0")
            assertThat(dashboard.metricValueOf(dashboard.downtimeCard)).hasText("-")
            assertThat(dashboard.recentIncidentsCard).containsText("No incidents in this period")
            assertThat(dashboard.leastReliableMonitorsCard).containsText("No monitor was down in this period")
            assertThat(dashboard.timelineBlocks("http", "bg-danger")).hasCount(0)
        }

        "the dashboard calls out the invalid certificates, but doesn't list them as incidents" {
            val now = getCurrentTimestamp()
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Invalid Certificate")
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(1),
                endedAt = null,
            )
            createSSLEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = SslStatus.INVALID,
                startedAt = now.minusHours(1),
                endedAt = null,
                error = "The certificate has expired",
            )

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.heading).hasText("Invalid certificates: 1")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-red"))
            assertThat(dashboard.certificatesCard.getByLabel("Invalid: 1")).hasText("1")
            assertThat(dashboard.certificates).hasCount(1)
            val certificate = dashboard.certificates.first()
            assertThat(certificate).containsText("Invalid Certificate")
            assertThat(certificate).containsText("The certificate has expired")
            assertThat(dashboard.statusDotOf(certificate)).hasClass(Pattern.compile("status-red"))
            // The certificates have a card of their own, the incidents are the uptime ones only
            assertThat(dashboard.recentIncidentsCard).containsText("No incidents in this period")
        }

        "the dashboard doesn't claim anything about the certificates that haven't been checked yet" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Unchecked Certificate")

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.certificatesCard).containsText("Waiting for the first certificate checks")
            assertThat(dashboard.certificatesCard.getByLabel("Pending: 1")).hasText("1")
            assertThat(dashboard.certificates).hasCount(0)
        }

        "the dashboard counts the ongoing incidents that don't fit into its list" {
            val now = getCurrentTimestamp()
            repeat(MORE_THAN_THE_LISTED_INCIDENTS) { index ->
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "Down Monitor $index",
                    sslCheckEnabled = false,
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(index + 1L),
                    endedAt = null,
                )
            }

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()

            assertThat(dashboard.heading)
                .hasText("Monitors down: $MORE_THAN_THE_LISTED_INCIDENTS of $MORE_THAN_THE_LISTED_INCIDENTS")
            assertThat(dashboard.incidents).hasCount(LISTED_INCIDENTS)
            assertThat(dashboard.incidents.first()).containsText("Down Monitor 0")
            assertThat(dashboard.moreOngoingIncidentsLink)
                .hasText("More ongoing incidents: ${MORE_THAN_THE_LISTED_INCIDENTS - LISTED_INCIDENTS}")
            assertThat(dashboard.moreOngoingIncidentsLink).hasAttribute("href", "/incidents?period=PT168H")
        }

        "the uptime timeline tells the slots with and without downtime and data apart" {
            val now = getCurrentTimestamp()
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Timeline", sslCheckEnabled = false)
            // Only checked for the last 5 hours, with an outage between 3 and 2 hours ago
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(CHECKED_FOR_HOURS),
                endedAt = now.minusHours(OUTAGE_STARTED_HOURS_AGO),
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = now.minusHours(OUTAGE_STARTED_HOURS_AGO),
                endedAt = now.minusHours(2),
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = now.minusHours(2),
                endedAt = null,
            )

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate("PT24H")

            assertThat(dashboard.timelineBlocks("http", "bg-danger").first()).isVisible()
            assertThat(dashboard.timelineBlocks("http", "bg-success").first()).isVisible()
            assertThat(dashboard.timelineBlocks("http", "text-muted").first()).isVisible()
            // The tooltips don't spell out any uptime, only the incidents, or the lack of any data
            assertThat(dashboard.timelineBlocks("http", "bg-danger").first())
                .hasAttribute("aria-label", Pattern.compile(".* · Incidents: 1$"))
            assertThat(dashboard.timelineBlocks("http", "text-muted").first())
                .hasAttribute("aria-label", Pattern.compile(".* · N/A$"))
            assertThat(dashboard.timelineBlocks("http", "bg-success").first())
                .hasAttribute("aria-label", Pattern.compile("^[^·]+ – [^·]+$"))
            // The resolved incident is counted by the key figures, and it took an hour to resolve
            assertThat(dashboard.metricValueOf(dashboard.incidentsCountCard)).hasText("1")
            assertThat(dashboard.metricValueOf(dashboard.downtimeCard)).hasText("1 hour")
            assertThat(dashboard.metricValueOf(dashboard.meanTimeToResolveCard)).hasText("1 hour")
            assertThat(dashboard.meanTimeToResolveCard).containsText("Resolved: 1")
        }

        "a malformed period falls back to the default one, instead of failing the dashboard" {
            createHealthyMonitor()

            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate("abc")

            assertThat(dashboard.heading).hasText("All systems operational")
            assertThat(dashboard.periodSelector).hasValue("PT168H")
            assertThat(dashboard.cardSubtitleOf(dashboard.recentIncidentsCard)).hasText("Last 7 days")
        }

        "every period of the selector has a label of its own on the cards" {
            createHealthyMonitor()

            val page = newPage()
            val dashboard = DashboardPage(page)
            listOf(
                "PT1H" to "Last hour",
                "PT6H" to "Last 6 hours",
                "PT12H" to "Last 12 hours",
                "PT24H" to "Last 24 hours",
                "PT168H" to "Last 7 days",
                "PT720H" to "Last 30 days",
            ).forEach { (period, label) ->
                dashboard.navigate(period)

                assertThat(dashboard.periodSelector).hasValue(period)
                assertThat(dashboard.cardSubtitleOf(dashboard.recentIncidentsCard)).hasText(label)
                assertThat(dashboard.cardSubtitleOf(dashboard.leastReliableMonitorsCard)).hasText(label)
            }
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
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile("status-yellow"))
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
        private const val OUTAGE_STARTED_HOURS_AGO = 3L
        private const val CHECKED_FOR_HOURS = 5L
        private const val LISTED_INCIDENTS = 6
        private const val MORE_THAN_THE_LISTED_INCIDENTS = LISTED_INCIDENTS + 2
        private const val LISTED_CERTIFICATES = 5
        private const val MORE_THAN_THE_LISTED_CERTIFICATES = LISTED_CERTIFICATES + 1
        private const val LISTED_MAINTENANCE_WINDOWS = 5
        private const val MORE_THAN_THE_LISTED_MAINTENANCE_WINDOWS = LISTED_MAINTENANCE_WINDOWS + 1

        private const val SMALL_SCREEN_WIDTH = 390
        private const val SMALL_SCREEN_HEIGHT = 844

        // The padding of the page and of its grid columns, between the first button and the edge of the screen
        private const val MAX_LEFT_GUTTER = 32.0
    }
}
