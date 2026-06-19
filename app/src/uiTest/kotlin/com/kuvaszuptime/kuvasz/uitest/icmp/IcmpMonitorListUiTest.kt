package com.kuvaszuptime.kuvasz.uitest.icmp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.icmp.IcmpMonitorListPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class IcmpMonitorListUiTest(private val icmpMonitorRepository: IcmpMonitorRepository) : UiTestSpec() {
    init {
        // Verifies the HTMX auto-refresh: the list polls its fragment and picks up changes without a navigation.
        "the ICMP monitor list auto-refreshes to show a monitor added after the page loaded" {
            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.emptyState).isVisible()

            // Add a monitor straight into the DB *after* the page has rendered — no navigation, no manual refresh.
            createIcmpMonitor(icmpMonitorRepository, monitorName = "Auto Refreshed")

            // The list polls its fragment every 15s, so the row appears on the next poll.
            assertThat(list.rowByName("Auto Refreshed"))
                .isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(AUTO_REFRESH_TIMEOUT_MS))
        }

        "pausing and resuming a monitor flips its status in the list via an HTMX refresh" {
            createIcmpMonitor(icmpMonitorRepository, monitorName = "Toggle Monitor")

            val page = newPage()
            val list = IcmpMonitorListPage(page)
            list.navigate()
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).containsText(Messages.paused())

            list.toggleMonitor("Toggle Monitor")
            assertThat(list.rowByName("Toggle Monitor")).not().containsText(Messages.paused())
        }
    }

    companion object {
        private const val AUTO_REFRESH_TIMEOUT_MS = 20_000.0
    }
}
