package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.pages.HttpMonitorListPage
import com.kuvaszuptime.kuvasz.uitest.pages.StatusPageDetailsPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

// Exercises the optimistic toggle + HTMX/reload loops: pausing a monitor and (un)publishing a status page.
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ToggleStateUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = HttpMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }

        "a status page can be unpublished from its detail page" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "SP Monitor")
            val statusPage = createStatusPage(
                dslContext,
                title = "Toggle Status Page",
                slug = "toggle-status-page",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage()
            val details = StatusPageDetailsPage(page)
            details.navigate(statusPage.id)
            assertThat(details.visibilityBadge(Messages.public())).isVisible()

            details.visibilityToggleButton.click()
            assertThat(details.visibilityBadge(Messages.private())).isVisible()
        }
    }
}
