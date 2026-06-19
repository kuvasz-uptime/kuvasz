package com.kuvaszuptime.kuvasz.uitest.http

import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.http.HttpMonitorDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class HttpMonitorDetailsUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a seeded monitor's detail page renders the uptime, latency and SSL blocks and the latency chart" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Detail Page Monitor")

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            assertThat(details.latencySection).isVisible()
            assertThat(details.sslSection).isVisible()
            // ApexCharts renders an <svg> even with no data (its no-data state).
            assertThat(details.latencyChartSvg).isVisible()
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Detail Toggle Monitor")

            val page = newPage()
            val details = HttpMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }
    }
}
