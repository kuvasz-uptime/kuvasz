package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.timeAgo
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
        lastIncident: OffsetDateTime? = null,
    ) = ActualUptimeStats(
        total = total,
        down = down,
        up = up,
        paused = paused,
        inProgress = inProgress,
        inMaintenance = inMaintenance,
        lastIncident = lastIncident,
    )

    fun ActualUptimeStats.outsideMaintenance(inMaintenance: MonitorStateCounts) = MonitorStateCounts(
        up = up - inMaintenance.up,
        down = down - inMaintenance.down,
        pending = inProgress - inMaintenance.pending,
    )

    fun overview(
        period: Duration,
        actual: ActualUptimeStats,
        inMaintenance: MonitorStateCounts = MonitorStateCounts(up = 0, down = 0, pending = 0),
        leastReliableMonitors: List<UnreliableMonitor> = emptyList(),
        recentIncidents: List<IncidentDto> = emptyList(),
        incidents: DashboardIncidentStats = DashboardIncidentStats(0, 0, 0, null),
        sslStats: SslStats = SslStats(invalid = 0, valid = 0, willExpire = 0, inProgress = 0),
        typeTimeline: List<UptimeTimelineSlot> = emptyList(),
        moreOngoingIncidents: Int = 0,
        certificatesWithIssues: List<HttpMonitorSummary> = emptyList(),
        maintenanceWindows: List<MaintenanceWindowDetailsDto> = emptyList(),
        moreMaintenanceWindows: Int = 0,
        history: HistoricalUptimeStatsDto = HistoricalUptimeStatsDto(
            period = period.toString(),
            incidents = 0,
            affectedMonitors = 0,
            uptimeRatio = null,
            totalDowntimeSeconds = 0,
        ),
    ) = DashboardOverview(
        period = period,
        uptimeStats = DashboardUptimeStats(
            actual = actual,
            history = history,
            incidents = incidents,
            outsideMaintenance = actual.outsideMaintenance(inMaintenance),
            inMaintenance = inMaintenance,
            sslStats = sslStats,
            timeline = emptyList(),
            byType = if (actual.total > 0) {
                listOf(
                    MonitorTypeUptimeStats(
                        type = MonitorType.PUSH,
                        actual = actual,
                        outsideMaintenance = actual.outsideMaintenance(inMaintenance),
                        inMaintenance = inMaintenance,
                        history = HistoricalUptimeStatsDto(period.toString(), 0, 0, null, 0),
                        timeline = typeTimeline,
                    )
                )
            } else {
                emptyList()
            },
            certificatesWithIssues = certificatesWithIssues,
            monitorsInMaintenance = emptySet(),
            leastReliableMonitors = leastReliableMonitors,
        ),
        recentIncidents = recentIncidents,
        moreOngoingIncidents = moreOngoingIncidents,
        maintenanceWindows = maintenanceWindows,
        moreMaintenanceWindows = moreMaintenanceWindows,
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
            val html = controller.dashboard(period = "PT720H")

            then("it should load the overview of that period") {
                html shouldContain "/fragments/dashboard?period=PT720H"
            }
        }

        `when`("a malformed period is requested") {
            val html = controller.dashboard(period = "7d")

            then("it should fall back to the default period, instead of failing") {
                html shouldContain "/fragments/dashboard?period=PT168H"
            }
        }

        `when`("every monitor type can be edited") {
            val html = controller.dashboard(period = null)

            then("it should offer a create modal for every one of them, with unique field ids") {
                MonitorType.entries.map { it.identifier }.forEach { slug ->
                    html shouldContain "data-bs-target=\"#create-$slug-monitor-modal\""
                    html shouldContain "id=\"create-$slug-monitor-modal\""
                    // The same field of every type gets the prefix of its type
                    html shouldContain "id=\"$slug-name-input\""
                }
                html shouldNotContain "id=\"name-input\""
                html shouldContain "id=\"http-url-input\""
                html shouldContain "id=\"push-clientSecret-input\""
            }
        }
    }

    given("the dashboard overview fragment") {

        listOf(
            null to Duration.ofDays(7),
            "PT0S" to Duration.ofDays(7),
            "-P1D" to Duration.ofDays(7),
            // Only the periods of the selector are accepted
            "PT0.01S" to Duration.ofDays(7),
            "P3D" to Duration.ofDays(7),
            // A malformed one doesn't fail the page either
            "abc" to Duration.ofDays(7),
            "PT1H" to Duration.ofHours(1),
            "PT720H" to Duration.ofDays(30),
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
                html shouldContain "Monitors down: 1 of 3"
                html shouldContain "/incidents?period=PT168H"
            }
        }

        listOf(
            "the paused monitors are left out of the count of the down ones" to
                overview(Duration.ofDays(7), actualStats(total = 4, up = 1, down = 1, paused = 2)) to
                ("Monitors down: 1 of 2" to "status-red"),
            // Only the one outside the maintenance window is alarming
            "a monitor is down outside a maintenance window, and another one within one" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 1, down = 2, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                ) to ("Monitors down: 1 of 3" to "status-red"),
            "the only monitor that is down is under maintenance" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 2, down = 1, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                ) to ("Down during maintenance: 1" to "status-gray"),
            "every monitor that isn't paused is waiting for its first check" to
                overview(Duration.ofDays(7), actualStats(total = 2, paused = 1, inProgress = 1)) to
                ("Waiting for the first checks" to "status-yellow"),
            // Neither up, nor pending, nor down outside a maintenance window, so the maintenance is all there is to say
            "every monitor that isn't paused is under maintenance, and none of them is down" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 2, inProgress = 1, inMaintenance = 3),
                    inMaintenance = MonitorStateCounts(up = 2, down = 0, pending = 1),
                ) to ("In maintenance: 3" to "status-gray"),
            "the only monitor that isn't paused is pending under maintenance" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 2, paused = 1, inProgress = 1, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 1),
                ) to ("In maintenance: 1" to "status-gray"),
            "some monitors are up outside maintenance, while the rest of them are pending under maintenance" to
                overview(
                    Duration.ofDays(7),
                    actualStats(total = 3, up = 1, inProgress = 2, inMaintenance = 2),
                    inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 2),
                ) to ("All systems operational" to "status-green"),
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
                    html shouldContain "class=\"status-indicator $expectedColor status-indicator-animated\""
                }
            }
        }

        `when`("monitors are down both outside and within a maintenance window") {

            then("the header should only count the ones outside the window as down") {
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 10, up = 6, down = 4, inMaintenance = 3),
                        inMaintenance = MonitorStateCounts(up = 0, down = 3, pending = 0),
                    )
                }
                val header = controller.dashboardOverview(period = null)
                    .substringBefore("data-testid=\"dashboard-uptime\"")

                header shouldContain ">Monitors down: 1 of 10</h2>"
                header shouldContain "aria-label=\"Down: 1\""
                header shouldContain "aria-label=\"Maintenance: 3\""
                header shouldNotContain "Down: 4"
            }
        }

        `when`("monitors of every state are under maintenance") {

            then("the header and the row of the type should count them only as in maintenance") {
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 8, up = 4, down = 2, inProgress = 2, inMaintenance = 4),
                        inMaintenance = MonitorStateCounts(up = 2, down = 1, pending = 1),
                    )
                }
                val html = controller.dashboardOverview(period = null)
                val header = html.substringBefore("data-testid=\"dashboard-uptime\"")
                val typeRow = html.substringAfter("data-testid=\"dashboard-monitor-type-push\"")

                listOf(header, typeRow).forEach { counts ->
                    // 2 + 1 + 1 + 4 = 8, every monitor is counted once
                    counts shouldContain "aria-label=\"Up: 2\""
                    counts shouldContain "aria-label=\"Down: 1\""
                    counts shouldContain "aria-label=\"Pending: 1\""
                    counts shouldContain "aria-label=\"Maintenance: 4\""
                }
            }
        }

        `when`("every monitor that isn't paused is under maintenance, without any of them being down") {

            then("the header shouldn't count them as up or pending, only as in maintenance") {
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 3, up = 2, inProgress = 1, inMaintenance = 3),
                        inMaintenance = MonitorStateCounts(up = 2, down = 0, pending = 1),
                    )
                }
                val header = controller.dashboardOverview(period = null)
                    .substringBefore("data-testid=\"dashboard-uptime\"")

                header shouldContain ">In maintenance: 3</h2>"
                val details = header.substringAfter("data-testid=\"dashboard-status-details\"")
                details shouldContain "aria-label=\"Up: 0\""
                details shouldContain "aria-label=\"Pending: 0\""
                details shouldContain "aria-label=\"Maintenance: 3\""
            }
        }

        listOf(
            "there wasn't any incident yet" to null,
            "there was an incident already" to getCurrentTimestamp().minusHours(2),
        ).forEach { (description, lastIncident) ->
            `when`(description) {

                then("the header should show the same figures below the verdict as always, even the zeros") {
                    every { provider().getOverview(any()) } answers {
                        overview(firstArg(), actualStats(total = 1, up = 1, lastIncident = lastIncident))
                    }
                    val details = controller.dashboardOverview(period = null)
                        .substringBefore("data-testid=\"dashboard-uptime\"")
                        .substringAfter("data-testid=\"dashboard-status-details\"")

                    details shouldContain "aria-label=\"Up: 1\""
                    details shouldContain "aria-label=\"Down: 0\""
                    // Only red if any of them is down
                    details shouldNotContain "text-red"
                    details shouldContain "aria-label=\"Maintenance: 0\""
                    details shouldContain "aria-label=\"Paused: 0\""
                    details shouldContain "aria-label=\"Pending: 0\""
                    details shouldContain if (lastIncident == null) {
                        "aria-label=\"No incidents\""
                    } else {
                        "aria-label=\"Last incident: ${lastIncident.timeAgo()}\""
                    }
                }
            }
        }

        listOf(
            "more certificates have issues than the card lists" to 2,
            "the card lists every certificate with issues" to 0,
        ).forEach { (description, expectedMore) ->
            `when`(description) {

                then("the ones that didn't fit should be called out, if there is any") {
                    val certificates = List(StatCalculator.CERTIFICATES_WITH_ISSUES_LIMIT) { index ->
                        HttpMonitorSummary(
                            id = index.toLong(),
                            name = "Monitor $index",
                            enabled = true,
                            category = null,
                            uptimeStatus = UptimeStatus.UP,
                            sslCheckEnabled = true,
                            sslStatus = SslStatus.INVALID,
                            sslValidUntil = null,
                            sslError = "Expired",
                        )
                    }
                    every { provider().getOverview(any()) } answers {
                        overview(
                            firstArg(),
                            actualStats(total = 10, up = 10),
                            sslStats = SslStats(
                                invalid = certificates.size + expectedMore,
                                valid = 0,
                                willExpire = 0,
                                inProgress = 0,
                            ),
                            certificatesWithIssues = certificates,
                        )
                    }
                    val card = controller.dashboardOverview(period = null)
                        .substringAfter("data-testid=\"dashboard-certificates\"")
                        .substringBefore("data-testid=\"dashboard-maintenance\"")

                    card.split("data-testid=\"dashboard-certificate\"") shouldHaveSize certificates.size + 1
                    // Every row links to the certificate checks of its monitor
                    card shouldContain "href=\"/http-monitors/0#http-monitor-details-ssl-events\""
                    if (expectedMore > 0) {
                        card shouldContain "<a href=\"/http-monitors\" class=\"list-group-item " +
                            "list-group-item-action\" data-testid=\"dashboard-more-certificates\">" +
                            "More certificates with issues: 2</a>"
                    } else {
                        card shouldNotContain "dashboard-more-certificates"
                    }
                }
            }
        }

        listOf(
            "more maintenance windows are active or upcoming than the card lists" to 2,
            "the card lists every active and upcoming maintenance window" to 0,
        ).forEach { (description, moreWindows) ->
            `when`(description) {

                then("the ones that didn't fit should be called out, if there is any") {
                    val now = getCurrentTimestamp()
                    val window = MaintenanceWindowDetailsDto(
                        id = 1,
                        name = "Window",
                        description = null,
                        enabled = true,
                        global = true,
                        showOnStatusPages = false,
                        cron = null,
                        start = now.minusHours(1),
                        duration = "PT2H",
                        monitors = emptySet(),
                        categories = emptySet(),
                        integrations = emptySet(),
                        active = true,
                        nextStart = null,
                        endsAt = now.plusHours(1),
                        createdAt = now,
                        updatedAt = now,
                    )
                    every { provider().getOverview(any()) } answers {
                        overview(
                            firstArg(),
                            actualStats(total = 1, up = 1),
                            maintenanceWindows = listOf(window),
                            moreMaintenanceWindows = moreWindows,
                        )
                    }
                    val card = controller.dashboardOverview(period = null)
                        .substringAfter("data-testid=\"dashboard-maintenance\"")

                    if (moreWindows > 0) {
                        card shouldContain "<a href=\"/maintenance-windows\" class=\"list-group-item " +
                            "list-group-item-action\" data-testid=\"dashboard-more-maintenance-windows\">" +
                            "More maintenance windows: 2</a>"
                    } else {
                        card shouldNotContain "dashboard-more-maintenance-windows"
                    }
                }
            }
        }

        listOf(
            "there wasn't any incident in the period" to (0 to null) to ("-" to "-"),
            // E.g. a monitor that has just gone down, and the flaps resolved within the same second
            "the incidents of the period were shorter than a second" to (2 to 0L) to
                ("Less than a second" to "Less than a second"),
        ).forEach { (scenario, expectedValues) ->
            val (description, figures) = scenario
            val (incidentCount, meanTimeToResolveSeconds) = figures
            val (expectedDowntime, expectedMeanTimeToResolve) = expectedValues
            `when`(description) {

                then("the downtime should be $expectedDowntime, and the MTTR $expectedMeanTimeToResolve") {
                    every { provider().getOverview(any()) } answers {
                        overview(
                            firstArg(),
                            actualStats(total = 1, up = 1),
                            history = HistoricalUptimeStatsDto(
                                period = firstArg<Duration>().toString(),
                                incidents = incidentCount,
                                affectedMonitors = incidentCount,
                                uptimeRatio = 1.0,
                                totalDowntimeSeconds = 0,
                            ),
                            incidents = DashboardIncidentStats(
                                ongoingOutsideMaintenance = 0,
                                ongoingInMaintenance = 0,
                                resolved = incidentCount,
                                meanTimeToResolveSeconds = meanTimeToResolveSeconds,
                            ),
                        )
                    }
                    val html = controller.dashboardOverview(period = null)
                    val downtimeCard = html.substringAfter("data-testid=\"dashboard-downtime\"")
                    val meanTimeToResolveCard = html.substringAfter("data-testid=\"dashboard-mttr\"")

                    downtimeCard shouldContain "<div class=\"h3 m-0\">$expectedDowntime</div>"
                    meanTimeToResolveCard shouldContain "<div class=\"h3 m-0\">$expectedMeanTimeToResolve</div>"
                }
            }
        }

        `when`("there are invalid certificates") {

            then("the verdict should spell them out, while the figures below it are about the monitors") {
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 2, up = 2),
                        sslStats = SslStats(invalid = 2, valid = 0, willExpire = 1, inProgress = 0),
                    )
                }
                val header = controller.dashboardOverview(period = null)
                    .substringBefore("data-testid=\"dashboard-uptime\"")
                val details = header.substringAfter("data-testid=\"dashboard-status-details\"")

                header shouldContain ">Invalid certificates: 2</h2>"
                details shouldNotContain "ertificates"
                details shouldContain "aria-label=\"Up: 2\""
            }
        }

        `when`("there are recent incidents of every type") {

            then("every row should link to its monitor, the SSL ones to its certificate checks") {
                val now = getCurrentTimestamp()
                val expectedLinks = listOf(
                    Triple(IncidentType.HTTP, 1L, "/http-monitors/1"),
                    Triple(IncidentType.SSL, 2L, "/http-monitors/2#http-monitor-details-ssl-events"),
                    Triple(IncidentType.PUSH, 3L, "/push-monitors/3"),
                    Triple(IncidentType.ICMP, 4L, "/icmp-monitors/4"),
                    Triple(IncidentType.TCP, 5L, "/tcp-monitors/5"),
                    Triple(IncidentType.DNS, 6L, "/dns-monitors/6"),
                    Triple(IncidentType.DOCKER, 7L, "/docker-monitors/7"),
                )
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 1, up = 1),
                        recentIncidents = expectedLinks.map { (type, monitorId, _) ->
                            IncidentDto(
                                monitorId = monitorId,
                                monitorName = "Monitor $monitorId",
                                isMonitorEnabled = true,
                                incidentType = type,
                                status = IncidentStatus.ONGOING,
                                details = null,
                                startedAt = now.minusHours(1),
                                endedAt = null,
                                updatedAt = now,
                            )
                        },
                    )
                }
                val rows = controller.dashboardOverview(period = null)
                    .split("data-testid=\"dashboard-incident\"")
                    .drop(1)

                rows shouldHaveSize expectedLinks.size
                rows.zip(expectedLinks).forEach { (row, expectedLink) ->
                    row shouldContain "<a href=\"${expectedLink.third}\""
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
                sslRow.split("<span>·</span>") shouldHaveSize 2
                dockerRow shouldContain "class=\"d-inline-flex icon-inline text-teal-lt-fg\""
                dockerRow shouldContain "aria-label=\"Docker\""
                dockerRow shouldContain "icon-tabler-brand-docker"
                dockerRow.split("<span>·</span>") shouldHaveSize 3
                dockerRow shouldContain "<span>1 hour</span>"
            }
        }

        listOf(
            "none of the certificates has been checked yet" to
                SslStats(invalid = 0, valid = 0, willExpire = 0, inProgress = 2) to
                "Waiting for the first certificate checks",
            "some of the certificates haven't been checked yet" to
                SslStats(invalid = 0, valid = 1, willExpire = 0, inProgress = 1) to
                "Every checked certificate is valid",
            "every certificate is checked" to
                SslStats(invalid = 0, valid = 2, willExpire = 0, inProgress = 0) to
                "Every certificate is valid",
        ).forEach { (scenario, expectedMessage) ->
            val (description, sslStats) = scenario
            `when`(description) {

                then("the certificates card should say \"$expectedMessage\"") {
                    every { provider().getOverview(any()) } answers {
                        overview(firstArg(), actualStats(total = 2, up = 2), sslStats = sslStats)
                    }
                    val card = controller.dashboardOverview(period = null)
                        .substringAfter("data-testid=\"dashboard-certificates\"")

                    card shouldContain ">$expectedMessage<"
                    // The pending ones are counted too, if there is any
                    if (sslStats.inProgress > 0) {
                        card shouldContain "aria-label=\"Pending: ${sslStats.inProgress}\""
                    } else {
                        card shouldNotContain "Pending:"
                    }
                }
            }
        }

        listOf(
            "monitors are down only outside a maintenance window" to (2 to 0) to
                listOf("<span class=\"text-red\">Ongoing: 2</span>"),
            "monitors are down only under maintenance" to (0 to 2) to
                listOf("<span>In maintenance: 2</span>"),
            "monitors are down both outside and under a maintenance window" to (1 to 3) to
                listOf("<span class=\"text-red\">Ongoing: 1</span>", "<span>In maintenance: 3</span>"),
        ).forEach { (scenario, expectedSpans) ->
            val (description, counts) = scenario
            val (outside, inMaintenance) = counts
            `when`(description) {

                then("only the ongoing incidents outside maintenance should be counted in red") {
                    every { provider().getOverview(any()) } answers {
                        overview(
                            firstArg(),
                            actualStats(total = 5, down = outside + inMaintenance, inMaintenance = inMaintenance),
                            inMaintenance = MonitorStateCounts(up = 0, down = inMaintenance, pending = 0),
                            incidents = DashboardIncidentStats(
                                ongoingOutsideMaintenance = outside,
                                ongoingInMaintenance = inMaintenance,
                                resolved = 0,
                                meanTimeToResolveSeconds = null,
                            ),
                        )
                    }
                    val card = controller.dashboardOverview(period = null)
                        .substringAfter("data-testid=\"dashboard-incidents-count\"")
                        .substringBefore("data-testid=\"dashboard-downtime\"")

                    expectedSpans.forEach { card shouldContain it }
                    if (outside == 0) card shouldNotContain "Ongoing:"
                    if (inMaintenance == 0) card shouldNotContain "In maintenance:"
                }

                then("the row of the type should only count the ones outside maintenance as down") {
                    every { provider().getOverview(any()) } answers {
                        overview(
                            firstArg(),
                            actualStats(total = 5, down = outside + inMaintenance, inMaintenance = inMaintenance),
                            inMaintenance = MonitorStateCounts(up = 0, down = inMaintenance, pending = 0),
                        )
                    }
                    val row = controller.dashboardOverview(period = null)
                        .substringAfter("data-testid=\"dashboard-monitor-type-push\"")

                    if (outside > 0) row shouldContain "aria-label=\"Down: $outside\"" else row shouldNotContain "Down:"
                }
            }
        }

        `when`("the timeline of a type has slots with and without data") {

            then("every block should tell its state by its color, and its tooltip shouldn't spell out any uptime") {
                val start = OffsetDateTime.parse("2026-09-30T10:00:00Z")
                fun slot(index: Long, uptimeSeconds: Long, downtimeSeconds: Long, incidents: Int) = UptimeTimelineSlot(
                    start = start.plusHours(index),
                    end = start.plusHours(index + 1),
                    uptimeSeconds = uptimeSeconds,
                    downtimeSeconds = downtimeSeconds,
                    incidents = incidents,
                )
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 1, up = 1),
                        typeTimeline = listOf(
                            slot(0, uptimeSeconds = 0, downtimeSeconds = 0, incidents = 0),
                            slot(1, uptimeSeconds = 3600, downtimeSeconds = 0, incidents = 0),
                            slot(2, uptimeSeconds = 1800, downtimeSeconds = 1800, incidents = 1),
                            // An incident that started too late in the slot to add a whole second of downtime yet
                            slot(3, uptimeSeconds = 3600, downtimeSeconds = 0, incidents = 1),
                        ),
                    )
                }
                val blocks = controller.dashboardOverview(period = null)
                    .substringAfter("dashboard-monitor-type-push")
                    .split("class=\"tracking-block ")
                    .drop(1)
                    .map { it.substringBefore("</div>") }

                blocks.map { it.substringBefore("\"") } shouldBe
                    listOf("text-muted", "bg-success", "bg-danger", "bg-danger")
                blocks.forEach { it shouldNotContain "uptime" }
                blocks[0] shouldContain "· N/A\""
                blocks.drop(1).forEach { it shouldNotContain "N/A" }
                blocks[1] shouldNotContain "Incidents"
                blocks.drop(2).forEach { it shouldContain "Incidents: 1" }
            }
        }

        `when`("there are more ongoing incidents than the dashboard lists") {

            then("the rest of them should be called out, linking to the incidents") {
                every { provider().getOverview(any()) } answers {
                    overview(
                        firstArg(),
                        actualStats(total = 8, down = 8),
                        recentIncidents = listOf(
                            IncidentDto(
                                monitorId = 1,
                                monitorName = "Monitor",
                                isMonitorEnabled = true,
                                incidentType = IncidentType.HTTP,
                                status = IncidentStatus.ONGOING,
                                details = null,
                                startedAt = getCurrentTimestamp().minusHours(1),
                                endedAt = null,
                                updatedAt = getCurrentTimestamp(),
                            ),
                        ),
                        moreOngoingIncidents = 7,
                    )
                }
                val html = controller.dashboardOverview(period = "PT720H")

                html.substringAfter("data-testid=\"dashboard-more-ongoing-incidents\"") shouldContain
                    ">More ongoing incidents: 7</a>"
                html shouldContain "href=\"/incidents?period=PT720H\" class=\"list-group-item"
            }
        }

        `when`("every ongoing incident is listed") {

            then("nothing else should be called out") {
                every { provider().getOverview(any()) } answers { overview(firstArg(), actualStats(total = 1, up = 1)) }

                controller.dashboardOverview(period = null) shouldNotContain "dashboard-more-ongoing-incidents"
            }
        }

        listOf(
            "PT1H" to "Last hour",
            "PT6H" to "Last 6 hours",
            "PT12H" to "Last 12 hours",
            "PT24H" to "Last 24 hours",
            "PT168H" to "Last 7 days",
            "PT720H" to "Last 30 days",
        ).forEach { (period, expectedLabel) ->
            `when`("the period is $period") {

                then("the cards should say \"$expectedLabel\"") {
                    every { provider().getOverview(any()) } answers {
                        overview(firstArg(), actualStats(total = 1, up = 1))
                    }
                    val html = controller.dashboardOverview(period = period)

                    html.split("data-testid=\"dashboard-card-subtitle\">$expectedLabel<") shouldHaveSize 3
                }
            }
        }

        `when`("none of the monitors went down during the period") {

            then("the least reliable monitors card should say so") {
                every { provider().getOverview(any()) } answers {
                    overview(firstArg(), actualStats(total = 1, up = 1))
                }
                val html = controller.dashboardOverview(period = null)

                html.substringAfter("dashboard-least-reliable-monitors") shouldContain
                    "No monitor was down in this period"
            }
        }

        `when`("some of the monitors went down during the period") {

            then("the least reliable monitors card should list them with their current state") {
                fun unreliableMonitor(
                    id: Long,
                    uptimeStatus: UptimeStatus? = UptimeStatus.UP,
                    inMaintenance: Boolean = false,
                ) = UnreliableMonitor(
                    id = NumericMonitorID(MonitorType.PUSH, id),
                    name = "Monitor $id",
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
                        actualStats(total = 4, up = 2, down = 1, inProgress = 1),
                        leastReliableMonitors = listOf(
                            unreliableMonitor(1, uptimeStatus = UptimeStatus.DOWN),
                            unreliableMonitor(2, inMaintenance = true),
                            unreliableMonitor(3),
                            unreliableMonitor(4, uptimeStatus = null),
                        ),
                    )
                }
                val rows = controller.dashboardOverview(period = null)
                    .substringAfter("dashboard-least-reliable-monitors")
                    .split("data-testid=\"dashboard-least-reliable-monitor\"")
                    .drop(1)

                rows.map { it.substringAfter("aria-label=\"").substringBefore("\"") } shouldBe
                    listOf("Down", "Maintenance", "Up", "In Progress")
                rows.map { row ->
                    row.substringBefore(" status-dot\" data-testid=\"dashboard-status-dot\"")
                        .substringAfterLast("class=\"")
                } shouldBe listOf(
                    "status-red status-dot-animated",
                    "status-gray",
                    "status-green",
                    "status-yellow",
                )
                rows.forEachIndexed { index, row ->
                    row shouldContain "href=\"/push-monitors/${index + 1}\""
                    row shouldContain ">Monitor ${index + 1}<"
                    row shouldContain "class=\"d-inline-flex icon-inline text-red-lt-fg\""
                    row shouldContain "aria-label=\"Push\""
                    row shouldContain "aria-label=\"50.00% uptime\""
                    row shouldContain "icon-tabler-percentage"
                    row shouldContain "aria-label=\"Incidents: 2\""
                    row shouldContain "icon-tabler-flame"
                    row shouldContain "aria-label=\"Downtime: 1 hour\""
                    row shouldContain "icon-tabler-clock-down"
                    row shouldNotContain "Incidents: 2<"
                    row.split("<span>·</span>") shouldHaveSize 4
                }
            }
        }
    }
}) {
    @MockBean(DashboardDataProvider::class)
    fun dashboardDataProviderMock(): DashboardDataProvider = mockk()
}
