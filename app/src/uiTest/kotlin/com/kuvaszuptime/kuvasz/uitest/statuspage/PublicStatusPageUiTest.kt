package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PublicStatusPageUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a public status page is reachable without authentication and shows its monitor" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Public API")
            val pageTitle = "Public Systems Status"
            val slug = "public-systems"
            createStatusPage(
                dslContext,
                title = pageTitle,
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.title(pageTitle).first()).isVisible()
            assertThat(statusPage.monitorCard(monitor.name)).isVisible()
        }

        "a monitor that is currently down is rendered with a DOWN status" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Down Service")
            // An ongoing (endedAt = null) DOWN event makes the monitor's current status DOWN.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createStatusPage(
                dslContext,
                title = "Down Status",
                slug = "down-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("down-status")

            assertThat(statusPage.monitorCardBody("Down Service")).containsText(UptimeStatus.DOWN.literal)
        }
    }
}
