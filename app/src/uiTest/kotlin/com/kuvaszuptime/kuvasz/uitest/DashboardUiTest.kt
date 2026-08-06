package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DashboardUiTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : UiTestSpec() {
    init {
        "the authenticated dashboard renders and HTMX swaps in the HTTP monitoring stats" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Dashboard Monitor")

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.heading).isVisible()
            // The section header and stat cards only appear once the HTMX `load` swap has replaced the spinner.
            assertThat(dashboard.httpSectionHeader).isVisible()
            assertThat(dashboard.httpStatCards.first()).isVisible()
            // The monitor checks a certificate too, so the SSL section is rendered next to the uptime one
            assertThat(dashboard.sslSectionHeader).isVisible()
        }

        "the dashboard shows a placeholder instead of its sections when there is no monitor at all" {
            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.emptyState).isVisible()
            MONITOR_TYPES.forEach { type ->
                assertThat(dashboard.statsRegionOf(type)).isEmpty()
            }
        }

        "the dashboard only renders the sections of the monitor types that have monitors" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Dashboard HTTP Monitor")
            createDnsMonitor(dnsMonitorRepository, monitorName = "Dashboard DNS Monitor")

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.sectionHeaderOf("http", "HTTP")).isVisible()
            assertThat(dashboard.sectionHeaderOf("dns", "DNS")).isVisible()
            // The types without a single monitor don't get a section of their own, and the placeholder is gone too
            assertThat(dashboard.emptyState).not().isAttached()
            listOf("push", "icmp", "tcp").forEach { type ->
                assertThat(dashboard.statsRegionOf(type)).isEmpty()
            }
        }

        "the monitors-with-issues block only shows up for the types that have something to call out" {
            val down = createHttpMonitor(httpMonitorRepository, monitorName = "Down Monitor")
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = down.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createDnsMonitor(dnsMonitorRepository, monitorName = "Healthy DNS Monitor")

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.issuesBlockOf("http")).isVisible()
            assertThat(dashboard.httpStatsRegion).containsText("Down Monitor")
            // The DNS section has every monitor of its type up, so it doesn't render an empty issues table
            assertThat(dashboard.sectionHeaderOf("dns", "DNS")).isVisible()
            assertThat(dashboard.issuesBlockOf("dns")).not().isAttached()
        }

        "the SSL section only shows up when there is an HTTP monitor that checks a certificate" {
            createHttpMonitor(httpMonitorRepository, monitorName = "No SSL Monitor", sslCheckEnabled = false)

            val page = newPage()
            val dashboard = DashboardPage(page)

            dashboard.navigate()

            assertThat(dashboard.httpSectionHeader).isVisible()
            assertThat(dashboard.sslSectionHeader).not().isAttached()
        }

        "refreshing the dashboard replaces the placeholder with the section of the freshly created monitor" {
            val page = newPage()
            val dashboard = DashboardPage(page)
            dashboard.navigate()
            assertThat(dashboard.emptyState).isVisible()

            // Added straight into the DB *after* the page has rendered — no navigation, no manual page reload.
            createHttpMonitor(httpMonitorRepository, monitorName = "Refreshed Monitor")
            dashboard.refresh()

            assertThat(dashboard.emptyState).not().isAttached()
            assertThat(dashboard.httpSectionHeader).isVisible()
        }
    }

    companion object {
        private val MONITOR_TYPES = listOf("http", "push", "icmp", "tcp", "dns")
    }
}
