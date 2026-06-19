package com.kuvaszuptime.kuvasz.uitest.push

import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.push.PushMonitorDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PushMonitorDetailsUiTest(private val pushMonitorRepository: PushMonitorRepository) : UiTestSpec() {
    init {
        "a seeded push monitor's detail page renders the heading and uptime block" {
            val monitor = createPushMonitor(pushMonitorRepository, monitorName = "Push Detail Monitor")

            val page = newPage()
            val details = PushMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            assertThat(details.content).isVisible()
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createPushMonitor(pushMonitorRepository, monitorName = "Push Detail Toggle Monitor")

            val page = newPage()
            val details = PushMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }
    }
}
