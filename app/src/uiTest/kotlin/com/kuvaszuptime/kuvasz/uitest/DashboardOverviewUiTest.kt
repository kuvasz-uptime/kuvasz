package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.dashboardActualStats
import com.kuvaszuptime.kuvasz.mocks.dashboardOverview
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.util.regex.Pattern

/**
 * The rendering of exactly given dashboard overviews. Their combinations are covered by the unit tests of the `ui`
 * module.
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class DashboardOverviewUiTest(
    private val dashboardDataProvider: DashboardDataProvider,
) : UiTestSpec() {

    @MockBean(DashboardDataProvider::class)
    fun dashboardDataProviderMock(): DashboardDataProvider = mockk()

    // Resolving the mock while the spec itself is being instantiated would be a circular dependency
    private fun provider() = getMock(dashboardDataProvider)

    private fun showDashboard(overview: (Duration) -> DashboardOverview): DashboardPage {
        every { provider().getOverview(any()) } answers { overview(firstArg()) }
        val dashboard = DashboardPage(newPage())
        dashboard.navigate()
        assertThat(dashboard.monitorTypesCard).isVisible()
        return dashboard
    }

    init {
        "the verdict and the monitor counts are rendered into the header, and the counts into the row of the type" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 8, up = 4, down = 2, inProgress = 2, inMaintenance = 4),
                    inMaintenance = MonitorStateCounts(up = 2, down = 1, pending = 1),
                )
            }

            assertThat(dashboard.heading).hasText("Monitors down: 1 of 8")
            assertThat(dashboard.statusIndicator).hasClass(Pattern.compile(".*status-red.*"))
            listOf(dashboard.statusDetails, dashboard.monitorTypeRow("push")).forEach { counts ->
                assertThat(counts.getByLabel("Up: 2")).hasText("2")
                assertThat(counts.getByLabel("Down: 1")).hasText("1")
                assertThat(counts.getByLabel("Down: 1")).hasClass(Pattern.compile(".*text-red.*"))
                assertThat(counts.getByLabel("Pending: 1")).hasText("1")
                assertThat(counts.getByLabel("Maintenance: 4")).hasText("4")
            }
            assertThat(dashboard.statusDetails.getByLabel("No incidents")).hasText("-")
        }

        "the incidents card counts the ongoing incidents, only the ones outside maintenance in red" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 5, down = 4, inMaintenance = 3),
                    inMaintenance = MonitorStateCounts(up = 0, down = 3, pending = 0),
                    incidents = DashboardIncidentStats(
                        ongoingOutsideMaintenance = 1,
                        ongoingInMaintenance = 3,
                        resolved = 0,
                        meanTimeToResolveSeconds = null,
                    ),
                )
            }
            val red = Pattern.compile(".*text-red.*")

            assertThat(dashboard.incidentsCountCard.getByText("Ongoing: 1")).hasClass(red)
            assertThat(dashboard.incidentsCountCard.getByText("In maintenance: 3")).not().hasClass(red)
        }

        "every recent incident links to its details page, and tells its type by an icon in the color of the type" {
            val now = getCurrentTimestamp()
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 2, up = 2),
                    recentIncidents = IncidentType.entries.mapIndexed { index, type ->
                        val isResolved = type == IncidentType.DOCKER
                        IncidentDto(
                            monitorId = index.toLong(),
                            monitorName = "${type.name} Monitor",
                            isMonitorEnabled = true,
                            incidentType = type,
                            eventId = index * EVENT_ID_STEP,
                            status = if (isResolved) IncidentStatus.RESOLVED else IncidentStatus.ONGOING,
                            details = null,
                            startedAt = now.minusHours(2),
                            endedAt = now.minusHours(1).takeIf { isResolved },
                            updatedAt = now,
                        )
                    },
                )
            }

            assertThat(dashboard.incidents).hasCount(IncidentType.entries.size)
            IncidentType.entries.forEachIndexed { index, type ->
                val link = dashboard.incident("${type.name} Monitor").getByRole(AriaRole.LINK)
                assertThat(link).hasAttribute("href", "/incidents/${type.name.lowercase()}/${index * EVENT_ID_STEP}")
            }
            val ssl = dashboard.incident("SSL Monitor")
            assertThat(ssl.getByLabel("SSL")).hasClass(Pattern.compile(".*text-yellow-lt-fg.*"))
            assertThat(ssl.getByLabel("SSL").locator(".icon-tabler-lock-open-2")).hasCount(1)
            assertThat(dashboard.statusDotOf(ssl)).hasAttribute("aria-label", "Ongoing")
            val docker = dashboard.incident("DOCKER Monitor")
            assertThat(docker.getByLabel("Docker")).hasClass(Pattern.compile(".*text-teal-lt-fg.*"))
            assertThat(docker.getByLabel("Docker").locator(".icon-tabler-brand-docker")).hasCount(1)
            assertThat(docker).containsText("1 hour")
            assertThat(dashboard.statusDotOf(docker)).hasAttribute("aria-label", "Resolved")
        }

        "every certificate with issues links to the certificate checks of its monitor" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 1, up = 1),
                    sslStats = SslStats(invalid = 1, valid = 0, willExpire = 0, inProgress = 0),
                    certificatesWithIssues = listOf(
                        HttpMonitorSummary(
                            id = CERTIFICATE_MONITOR_ID,
                            name = "Expired Monitor",
                            enabled = true,
                            category = null,
                            uptimeStatus = UptimeStatus.UP,
                            sslCheckEnabled = true,
                            sslStatus = SslStatus.INVALID,
                            sslValidUntil = null,
                            sslError = "Expired",
                        ),
                    ),
                )
            }

            assertThat(dashboard.certificates.getByRole(AriaRole.LINK))
                .hasAttribute("href", "/http-monitors/$CERTIFICATE_MONITOR_ID#http-monitor-details-ssl-events")
        }

        "a least reliable monitor is listed with its current state and its figures" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 1, down = 1),
                    leastReliableMonitors = listOf(
                        UnreliableMonitor(
                            id = NumericMonitorID(MonitorType.PUSH, UNRELIABLE_MONITOR_ID),
                            name = "Flaky Monitor",
                            uptimeStatus = UptimeStatus.DOWN,
                            inMaintenance = false,
                            history = HistoricalUptimeStatsDto(
                                period = "PT168H",
                                incidents = 2,
                                affectedMonitors = 1,
                                uptimeRatio = 0.5,
                                totalDowntimeSeconds = HOUR,
                            ),
                        ),
                    ),
                )
            }
            val row = dashboard.leastReliableMonitors.first()

            assertThat(dashboard.statusDotOf(row)).hasAttribute("aria-label", "Down")
            assertThat(dashboard.statusDotOf(row)).hasClass(Pattern.compile(".*status-dot-animated.*"))
            assertThat(row.getByRole(AriaRole.LINK)).hasAttribute("href", "/push-monitors/$UNRELIABLE_MONITOR_ID")
            assertThat(row).containsText("Flaky Monitor")
            assertThat(row.getByLabel("Push")).hasClass(Pattern.compile(".*text-red-lt-fg.*"))
            assertThat(row.getByLabel("50.00% uptime")).isVisible()
            assertThat(row.getByLabel("Incidents: 2")).hasText("2")
            assertThat(row.getByLabel("Downtime: 1 hour")).isVisible()
        }
    }

    private companion object {
        const val EVENT_ID_STEP = 10L
        const val CERTIFICATE_MONITOR_ID = 42L
        const val UNRELIABLE_MONITOR_ID = 7L
        const val HOUR = 3600L
    }
}
