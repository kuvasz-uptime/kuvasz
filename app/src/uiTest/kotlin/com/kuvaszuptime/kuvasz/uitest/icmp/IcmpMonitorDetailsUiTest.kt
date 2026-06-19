package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorDetailsUiTest(private val icmpMonitorRepository: IcmpMonitorRepository) : UiTestSpec() {
    init {
        "a seeded ICMP monitor's detail page renders the uptime block and the latency/packet-loss charts" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Detail Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            // ApexCharts renders an <svg> into each container even with no data (its no-data state).
            assertThat(details.latencyChartSvg).isVisible()
            assertThat(details.packetLossChartSvg).isVisible()
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP Detail Toggle Monitor")

            val page = newPage()
            val details = IcmpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }
    }
}
