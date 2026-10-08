package com.kuvaszuptime.kuvasz.uitest.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createDockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.docker.DockerMonitorDetailsPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DockerMonitorDetailsUiTest(private val dockerMonitorRepository: DockerMonitorRepository) : UiTestSpec() {
    init {
        "a seeded Docker monitor's detail page renders the uptime block and the resource chart" {
            val monitor = createDockerMonitor(dockerMonitorRepository, monitorName = "Docker Detail Monitor")

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.heading(monitor.name)).isVisible()
            assertThat(details.uptimeSection).isVisible()
            // ApexCharts renders an <svg> into the container even with no data (its no-data state).
            assertThat(details.metricsChartSvg).isVisible()
            assertThat(details.metricsPeriodSelector).hasValue("PT24H")
        }

        /*
         The ceiling starts collapsed: a container started without a memory limit reports the host's whole RAM as
         one, which would flatten the usage against the bottom of the scale. It stays in the legend so it can be
         turned on when it is the thing being looked at.
        */
        "the memory limit series starts collapsed and can be toggled on from the legend" {
            val monitor = createDockerMonitor(
                dockerMonitorRepository,
                monitorName = "Docker Memory Limit Monitor",
                metricsHistoryEnabled = true,
            )
            createDockerMetricsLogRecord(dslContext, monitorId = monitor.id)

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            val limit = Messages.dockerMemoryLimit()
            assertThat(details.chartLegendItem(limit)).isVisible()
            assertThat(details.collapsedChartLegendItem(limit)).hasCount(1)

            // Clicking its legend entry brings it back
            details.chartLegendItem(limit).click()
            assertThat(details.collapsedChartLegendItem(limit)).hasCount(0)
        }

        "the image the container was last seen with is shown in the heading, once it is known" {
            val inspected = createDockerMonitor(dockerMonitorRepository, monitorName = "Inspected Docker Monitor")
            createDockerUptimeEventRecord(
                dslContext,
                monitorId = inspected.id,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
                image = "nginx:1.27",
                restartCount = 3,
                containerCreatedAt = OffsetDateTime.parse("2026-10-07T06:59:12Z"),
            )
            val unchecked = createDockerMonitor(dockerMonitorRepository, monitorName = "Unchecked Docker Monitor")

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(inspected.id)
            assertThat(details.imageBadge).hasText("nginx:1.27")

            with(details.imageBadgeTooltip.shouldNotBeNull()) {
                // The values are highlighted, the way the chart tooltips show them
                shouldContain("${Messages.dockerImageLabel()}: <strong>nginx:1.27</strong><br>")
                shouldContain("${Messages.dockerRestartCountLabel()}: <strong>3</strong><br>")
                // The timestamp is kept on one line, so the narrow tooltip doesn't break it between the date and time
                shouldContain(
                    "${Messages.dockerContainerCreatedAtLabel()}: <strong class=\"text-nowrap\">2026/10/07 "
                )
            }

            details.navigate(unchecked.id)
            assertThat(details.heading(unchecked.name)).isVisible()
            assertThat(details.imageBadge).hasCount(0)
        }

        "a monitor whose Docker host is not configured anymore is flagged with a badge in its heading" {
            val dangling = createDockerMonitor(
                dockerMonitorRepository,
                monitorName = "Dangling Docker Detail Monitor",
                dockerHost = "removed-host",
            )
            val configured = createDockerMonitor(
                dockerMonitorRepository,
                monitorName = "Configured Docker Detail Monitor",
                dockerHost = "local",
            )

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(dangling.id)
            assertThat(details.danglingHostBadge).isVisible()
            assertThat(details.danglingHostBadge).containsText(Messages.dockerHostNotConfiguredBadge())

            details.navigate(configured.id)
            assertThat(details.heading(configured.name)).isVisible()
            assertThat(details.danglingHostBadge).hasCount(0)
        }

        "the monitor can be paused and resumed from its detail page" {
            val monitor = createDockerMonitor(dockerMonitorRepository, monitorName = "Docker Detail Toggle Monitor")

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.pauseControl).isVisible()

            details.toggleButton.click()
            assertThat(details.resumeControl).isVisible()
            // The paused state arrives via the heading's refresh, which must not nest the heading
            assertThat(details.uptimeSummary).containsText(Messages.monitorIsPaused())
            assertThat(details.headingElements).hasCount(1)

            details.toggleButton.click()
            assertThat(details.pauseControl).isVisible()
        }

        "a monitor under an active maintenance window shows the maintenance indicator in its heading" {
            val monitor = createDockerMonitor(dockerMonitorRepository, monitorName = "Maintained Docker Detail Monitor")
            createMaintenanceWindow(
                dslContext,
                name = "Docker detail maintenance",
                enabled = true,
                monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name)),
            )

            val page = newPage()
            val details = DockerMonitorDetailsPage(page)
            details.navigate(monitor.id)

            assertThat(details.maintenanceIndicator).isVisible()
        }
    }
}
