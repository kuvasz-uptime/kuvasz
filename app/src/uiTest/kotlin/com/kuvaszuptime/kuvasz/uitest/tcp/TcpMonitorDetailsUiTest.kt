package com.kuvaszuptime.kuvasz.uitest.tcp

import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.tcp.TcpMonitorDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class TcpMonitorDetailsUiTest(private val tcpMonitorRepository: TcpMonitorRepository) : UiTestSpec() {
    init {
        "a seeded TCP monitor's detail page renders the uptime block and the connect-latency chart" {
            val monitor = createTcpMonitor(tcpMonitorRepository, monitorName = "TCP Detail Monitor")

            val page = newPage()
            val details = TcpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            // ApexCharts renders an <svg> into the container even with no data (its no-data state).
            assertThat(details.latencyChartSvg).isVisible()
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createTcpMonitor(tcpMonitorRepository, monitorName = "TCP Detail Toggle Monitor")

            val page = newPage()
            val details = TcpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }

        "a monitor under an active maintenance window shows the maintenance indicator in its heading" {
            val monitor = createTcpMonitor(tcpMonitorRepository, monitorName = "Maintained TCP Detail Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "TCP detail maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.TCP, monitor.name)),
            )

            val page = newPage()
            val details = TcpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.maintenanceIndicator).isVisible()
        }
    }
}
