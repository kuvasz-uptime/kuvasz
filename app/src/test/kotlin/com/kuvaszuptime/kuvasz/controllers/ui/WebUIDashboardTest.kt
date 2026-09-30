package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class WebUIDashboardTest(
    private val controller: WebUIController,
    private val dashboardDataProvider: DashboardDataProvider,
) : DatabaseBehaviorSpec({

    fun actualStats(
        total: Int = 0,
        up: Int = 0,
        down: Int = 0,
        paused: Int = 0,
        inProgress: Int = 0,
        inMaintenance: Int = 0,
    ) = ActualUptimeStats(
        total = total,
        down = down,
        up = up,
        paused = paused,
        inProgress = inProgress,
        inMaintenance = inMaintenance,
        lastIncident = null,
    )

    fun overview(
        period: Duration,
        actual: ActualUptimeStats,
        downInMaintenance: Int = 0,
        leastReliableMonitors: List<UnreliableMonitor> = emptyList(),
        recentIncidents: List<IncidentDto> = emptyList(),
    ) = DashboardOverview(
        period = period,
        uptimeStats = DashboardUptimeStats(
            actual = actual,
            history = HistoricalUptimeStatsDto(
                period = period.toString(),
                incidents = 0,
                affectedMonitors = 0,
                uptimeRatio = null,
                totalDowntimeSeconds = 0,
            ),
            incidents = DashboardIncidentStats(ongoing = 0, resolved = 0, meanTimeToResolveSeconds = null),
            downInMaintenance = downInMaintenance,
            sslStats = HttpMonitoringStatsDto.ActualMonitoringStats.SslStats(
                invalid = 0,
                valid = 0,
                willExpire = 0,
                inProgress = 0,
            ),
            timeline = emptyList(),
            byType = if (actual.total > 0) {
                listOf(
                    MonitorTypeUptimeStats(
                        type = MonitorType.PUSH,
                        actual = actual,
                        history = HistoricalUptimeStatsDto(period.toString(), 0, 0, null, 0),
                        timeline = emptyList(),
                    )
                )
            } else {
                emptyList()
            },
            certificatesWithIssues = emptyList(),
            monitorsInMaintenance = emptySet(),
            leastReliableMonitors = leastReliableMonitors,
        ),
        recentIncidents = recentIncidents,
        maintenanceWindows = emptyList(),
        maintenanceLookahead = Duration.ofDays(7),
    )

    // Resolving the mock while the spec itself is being instantiated would be a circular dependency
    fun provider() = getMock(dashboardDataProvider)

    given("the dashboard page") {

        `when`("no period is requested") {
            val html = controller.dashboard(period = null)

            then("it should load the overview of the default period") {
                html shouldContain "/fragments/dashboard?period=PT168H"
            }
        }

        `when`("a period is requested") {
            val html = controller.dashboard(period = Duration.ofDays(30))

            then("it should load the overview of that period") {
                html shouldContain "/fragments/dashboard?period=PT720H"
            }
        }
    }

    given("the dashboard overview fragment") {

        listOf(
            null to Duration.ofDays(7),
            Duration.ZERO to Duration.ofDays(7),
            Duration.ofDays(-1) to Duration.ofDays(7),
            // Only the periods of the selector are accepted
            Duration.ofMillis(10) to Duration.ofDays(7),
            Duration.ofDays(3) to Duration.ofDays(7),
            Duration.ofHours(1) to Duration.ofHours(1),
            Duration.ofDays(30) to Duration.ofDays(30),
        ).forEach { (requestedPeriod, expectedPeriod) ->
            `when`("the requested period is $requestedPeriod") {

                then("it should calculate the overview of $expectedPeriod") {
                    // The mock is refreshed after every test, so it has to be called and verified in the same one
                    every { provider().getOverview(any()) } answers { overview(firstArg(), actualStats()) }
                    controller.dashboardOverview(period = requestedPeriod)

                    verify { provider().getOverview(expectedPeriod) }
                }
            }
        }

        `when`("there isn't any monitor") {
            every { provider().getOverview(any()) } answers { overview(firstArg(), actualStats()) }
            val html = controller.dashboardOverview(period = null)

            then("it should render the empty state only") {
                html shouldContain "empty-state"
                html shouldNotContain "dashboard-monitor-types"
            }
        }

        `when`("there are monitors") {
            every { provider().getOverview(any()) } answers {
                overview(firstArg(), actualStats(total = 3, up = 2, down = 1))
            }
            val html = controller.dashboardOverview(period = null)

            then("it should render the overview with the verdict swapped into the page header") {
                html shouldNotContain "empty-state"
                html shouldContain "dashboard-monitor-types"
                html shouldContain "dashboard-monitor-type-push"
                html shouldContain "hx-swap-oob"
                html shouldContain "1 of 3 monitors down"
                html shouldContain "/incidents?period=PT168H"
            }
        }

        listOf(
            "the paused monitors are left out of the count of the down ones" to
                overview(Duration.ofDays(7), actualStats(total = 4, up = 1, down = 1, paused = 2)) to
                ("1 of 2 monitors down" to "status-red"),
            "a monitor is down outside a maintenance window, and another one within one" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 1, down = 2, inMaintenance = 1),
                    downInMaintenance = 1,
                ) to ("2 of 3 monitors down" to "status-red"),
            "the only monitor that is down is under maintenance" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 2, down = 1, inMaintenance = 1),
                    downInMaintenance = 1,
                ) to ("Down during maintenance: 1" to "status-gray"),
            "every monitor that isn't paused is waiting for its first check" to
                overview(Duration.ofDays(7), actualStats(total = 2, paused = 1, inProgress = 1)) to
                ("Waiting for the first checks" to "status-yellow"),
            "every monitor is paused" to
                overview(Duration.ofDays(7), actualStats(total = 2, paused = 2)) to
                ("Every monitor is paused" to "status-cyan"),
            "some of the monitors are up, and the rest of them are pending or paused" to
                overview(Duration.ofDays(7), actualStats(total = 3, up = 1, paused = 1, inProgress = 1)) to
                ("All systems operational" to "status-green"),
        ).forEach { (scenario, expectedVerdict) ->
            val (description, dashboardOverview) = scenario
            val (expectedTitle, expectedColor) = expectedVerdict
            `when`(description) {

                then("the verdict should be \"$expectedTitle\", with the $expectedColor indicator") {
                    every { provider().getOverview(any()) } returns dashboardOverview
                    val html = controller.dashboardOverview(period = null)

                    html shouldContain ">$expectedTitle</h2>"
                    // The same colors as the monitor states have everywhere else
                    html shouldContain "class=\"status-indicator $expectedColor status-indicator-animated\""
                }
            }
        }

        `when`("a type has monitors waiting for their first check") {

            then("its row should tell how many of them are pending") {
                every { provider().getOverview(any()) } answers {
                    overview(firstArg(), actualStats(total = 2, up = 1, inProgress = 1))
                }
                val html = controller.dashboardOverview(period = null)

                // The header is rendered first, and it tells the same
                html.substringAfter("dashboard-monitor-type-push") shouldContain "title=\"Pending: 1\""
            }
        }

        `when`("there are recent incidents") {

            then("their rows should show the type as an icon in the color of the type") {
                val now = getCurrentTimestamp()
                fun incident(type: IncidentType, status: IncidentStatus, endedAt: OffsetDateTime?) = IncidentDto(
                    monitorId = 1,
                    monitorName = "Monitor",
                    isMonitorEnabled = true,
                    incidentType = type,
                    status = status,
                    details = null,
                    startedAt = now.minusHours(2),
                    endedAt = endedAt,
                    updatedAt = endedAt ?: now,
                )
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 2, up = 2),
                        recentIncidents = listOf(
                            incident(IncidentType.SSL, IncidentStatus.ONGOING, endedAt = null),
                            incident(IncidentType.DOCKER, IncidentStatus.RESOLVED, endedAt = now.minusHours(1)),
                        ),
                    )
                }
                val (sslRow, dockerRow) = controller.dashboardOverview(period = null)
                    .split("data-testid=\"dashboard-incident\"")
                    .drop(1)

                sslRow shouldContain "class=\"d-inline-flex icon-inline text-yellow-lt-fg\""
                sslRow shouldContain "aria-label=\"SSL\""
                sslRow shouldContain "icon-tabler-lock-open"
                // The type and when it started
                sslRow.split("<span>·</span>") shouldHaveSize 2
                dockerRow shouldContain "class=\"d-inline-flex icon-inline text-teal-lt-fg\""
                dockerRow shouldContain "aria-label=\"Docker\""
                dockerRow shouldContain "icon-tabler-brand-docker"
                // The type, how long it lasted and when it got resolved
                dockerRow.split("<span>·</span>") shouldHaveSize 3
                dockerRow shouldContain "<span>1 hour</span>"
            }
        }

        `when`("none of the monitors went down during the period") {

            then("the least reliable monitors card should say so") {
                every { provider().getOverview(any()) } answers {
                    overview(firstArg(), actualStats(total = 1, up = 1))
                }
                val html = controller.dashboardOverview(period = null)

                html.substringAfter("dashboard-least-reliable-monitors") shouldContain
                    "No monitor was down in the last 7 days"
            }
        }

        `when`("some of the monitors went down during the period") {

            then("the least reliable monitors card should list them with their current state") {
                fun unreliableMonitor(
                    id: Long,
                    enabled: Boolean = true,
                    uptimeStatus: UptimeStatus? = UptimeStatus.UP,
                    inMaintenance: Boolean = false,
                ) = UnreliableMonitor(
                    id = NumericMonitorID(MonitorType.PUSH, id),
                    name = "Monitor $id",
                    enabled = enabled,
                    uptimeStatus = uptimeStatus,
                    inMaintenance = inMaintenance,
                    history = HistoricalUptimeStatsDto(
                        period = "PT168H",
                        incidents = 2,
                        affectedMonitors = 1,
                        uptimeRatio = 0.5,
                        totalDowntimeSeconds = 3600,
                    ),
                )
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 5, up = 2, down = 1, paused = 1, inProgress = 1),
                        leastReliableMonitors = listOf(
                            unreliableMonitor(1, uptimeStatus = UptimeStatus.DOWN),
                            unreliableMonitor(2, enabled = false),
                            unreliableMonitor(3, inMaintenance = true),
                            unreliableMonitor(4),
                            unreliableMonitor(5, uptimeStatus = null),
                        ),
                    )
                }
                val rows = controller.dashboardOverview(period = null)
                    .substringAfter("dashboard-least-reliable-monitors")
                    .split("data-testid=\"dashboard-least-reliable-monitor\"")
                    .drop(1)

                rows.map { it.substringAfter("aria-label=\"").substringBefore("\"") } shouldBe
                    listOf("Down", "Paused", "Maintenance", "Up", "In Progress")
                // The same colors as the monitor states have everywhere else
                rows.map { row ->
                    row.substringBefore(" status-dot\" data-testid=\"dashboard-status-dot\"")
                        .substringAfterLast("class=\"")
                } shouldBe listOf(
                    "status-red status-dot-animated",
                    "status-cyan",
                    "status-gray",
                    "status-green",
                    "status-yellow",
                )
                rows.forEachIndexed { index, row ->
                    row shouldContain "href=\"/push-monitors/${index + 1}\""
                    row shouldContain ">Monitor ${index + 1}<"
                    // The type is an icon in the color of the type, the figures are icons too, all named by tooltips
                    row shouldContain "class=\"d-inline-flex icon-inline text-red-lt-fg\""
                    row shouldContain "aria-label=\"Push\""
                    row shouldContain "aria-label=\"50.00% uptime\""
                    row shouldContain "icon-tabler-percentage"
                    row shouldContain "aria-label=\"Incidents: 2\""
                    row shouldContain "icon-tabler-flame"
                    row shouldContain "aria-label=\"Downtime: 1 hour\""
                    row shouldContain "icon-tabler-clock-down"
                    row shouldNotContain "Incidents: 2<"
                    // The same dots between the sections as the other rows have
                    row.split("<span>·</span>") shouldHaveSize 4
                }
            }
        }
    }
}) {
    @MockBean(DashboardDataProvider::class)
    fun dashboardDataProviderMock(): DashboardDataProvider = mockk()
}
