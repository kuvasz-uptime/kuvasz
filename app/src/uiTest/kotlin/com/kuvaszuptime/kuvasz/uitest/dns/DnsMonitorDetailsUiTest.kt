package com.kuvaszuptime.kuvasz.uitest.dns

import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.dns.DnsMonitorDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DnsMonitorDetailsUiTest(
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val snapshotRepository: DnsResolutionSnapshotRepository,
) : UiTestSpec() {
    init {
        "a seeded DNS monitor's detail page renders the uptime block and the resolution-latency chart" {
            val monitor = createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Detail Monitor")

            val page = newPage()
            val details = DnsMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            // ApexCharts renders an <svg> into the container even with no data (its no-data state).
            assertThat(details.latencyChartSvg).isVisible()
        }

        "a recorded resolution snapshot is rendered (and auto-loaded) on the detail page" {
            val monitor = createDnsMonitor(
                dnsMonitorRepository,
                monitorName = "DNS Snapshot Monitor",
                driftDetectionEnabled = true,
            )
            snapshotRepository.upsert(
                monitor.id,
                mapOf(
                    DnsRecordType.A to listOf("1.2.3.4", "5.6.7.8"),
                    DnsRecordType.MX to listOf("10 mail.example.com"),
                ),
            )

            val page = newPage()
            val details = DnsMonitorDetailsPage(page)
            details.navigate(monitor.id)

            // The snapshot block is populated by an HTMX load-trigger, so its recorded records appear without action.
            assertThat(details.snapshotSection).containsText("1.2.3.4")
            assertThat(details.snapshotSection).containsText("5.6.7.8")
            assertThat(details.snapshotSection).containsText("10 mail.example.com")
        }

        "the snapshot block is left out entirely when drift detection is disabled" {
            val monitor = createDnsMonitor(
                dnsMonitorRepository,
                monitorName = "DNS Driftless Monitor",
                driftDetectionEnabled = false,
            )

            val page = newPage()
            val details = DnsMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.uptimeSection).isVisible()
            // Nothing to show and nothing to poll for: the block is not rendered at all.
            assertThat(details.snapshotSection).hasCount(0)
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createDnsMonitor(dnsMonitorRepository, monitorName = "DNS Detail Toggle Monitor")

            val page = newPage()
            val details = DnsMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }

        "a monitor under an active maintenance window shows the maintenance indicator in its heading" {
            val monitor = createDnsMonitor(dnsMonitorRepository, monitorName = "Maintained DNS Detail Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "DNS detail maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.DNS, monitor.name)),
            )

            val page = newPage()
            val details = DnsMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.maintenanceIndicator).isVisible()
        }
    }
}
