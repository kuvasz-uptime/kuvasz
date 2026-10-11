package com.kuvaszuptime.kuvasz.uitest

import com.kuvaszuptime.kuvasz.i18n.Messages
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
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.common.tooltipText
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.timeAgo
import com.microsoft.playwright.Locator
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.OffsetDateTime
import java.util.regex.Pattern

/**
 * The figures of the dashboard, rendered from overviews that are given exactly, instead of being calculated from
 * seeded monitors like in [DashboardUiTest], so every combination of them can be covered.
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

    private fun Locator.shouldBeRed(isRed: Boolean = true) {
        val red = Pattern.compile(".*text-red.*")
        if (isRed) assertThat(this).hasClass(red) else assertThat(this).not().hasClass(red)
    }

    init {
        listOf(
            "the paused monitors are left out of the count of the down ones" to
                { period: Duration ->
                    dashboardOverview(period, dashboardActualStats(total = 4, up = 1, down = 1, paused = 2))
                } to
                ("Monitors down: 1 of 2" to "status-red"),
            // Only the one outside the maintenance window is alarming
            "a monitor is down outside a maintenance window, and another one within one" to
                { period: Duration ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 3, up = 1, down = 2, inMaintenance = 1),
                        inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                    )
                } to ("Monitors down: 1 of 3" to "status-red"),
            "the only monitor that is down is under maintenance" to
                { period: Duration ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 3, up = 2, down = 1, inMaintenance = 1),
                        inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                    )
                } to ("Down during maintenance: 1" to "status-gray"),
            "every monitor that isn't paused is waiting for its first check" to
                { period: Duration ->
                    dashboardOverview(period, dashboardActualStats(total = 2, paused = 1, inProgress = 1))
                } to
                ("Waiting for the first checks" to "status-yellow"),
            // Neither up, nor pending, nor down outside a maintenance window, so the maintenance is all there is to say
            "every monitor that isn't paused is under maintenance, and none of them is down" to
                { period: Duration ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 3, up = 2, inProgress = 1, inMaintenance = 3),
                        inMaintenance = MonitorStateCounts(up = 2, down = 0, pending = 1),
                    )
                } to ("In maintenance: 3" to "status-gray"),
            "the only monitor that isn't paused is pending under maintenance" to
                { period: Duration ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 2, paused = 1, inProgress = 1, inMaintenance = 1),
                        inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 1),
                    )
                } to ("In maintenance: 1" to "status-gray"),
            "some monitors are up outside maintenance, while the rest of them are pending under maintenance" to
                { period: Duration ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 3, up = 1, inProgress = 2, inMaintenance = 2),
                        inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 2),
                    )
                } to ("All systems operational" to "status-green"),
            "some of the monitors are up, and the rest of them are pending or paused" to
                { period: Duration ->
                    dashboardOverview(period, dashboardActualStats(total = 3, up = 1, paused = 1, inProgress = 1))
                } to ("All systems operational" to "status-green"),
        ).forEach { (scenario, expectedVerdict) ->
            val (description, overview) = scenario
            val (expectedTitle, expectedColor) = expectedVerdict

            "the verdict is \"$expectedTitle\" when $description" {
                val dashboard = showDashboard(overview)

                assertThat(dashboard.heading).hasText(expectedTitle)
                assertThat(dashboard.statusIndicator).hasClass(Pattern.compile(".*$expectedColor.*"))
            }
        }

        "the header counts the monitors down under maintenance only as in maintenance" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 10, up = 6, down = 4, inMaintenance = 3),
                    inMaintenance = MonitorStateCounts(up = 0, down = 3, pending = 0),
                )
            }

            assertThat(dashboard.heading).hasText("Monitors down: 1 of 10")
            assertThat(dashboard.statusDetails.getByLabel("Down: 1")).hasText("1")
            assertThat(dashboard.statusDetails.getByLabel("Maintenance: 3")).hasText("3")
            assertThat(dashboard.statusDetails.getByLabel("Down: 4")).not().isAttached()
        }

        "every monitor is counted once by the header and by the row of its type, even under maintenance" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 8, up = 4, down = 2, inProgress = 2, inMaintenance = 4),
                    inMaintenance = MonitorStateCounts(up = 2, down = 1, pending = 1),
                )
            }

            // 2 + 1 + 1 + 4 = 8
            listOf(dashboard.statusDetails, dashboard.monitorTypeRow("push")).forEach { counts ->
                assertThat(counts.getByLabel("Up: 2")).hasText("2")
                assertThat(counts.getByLabel("Down: 1")).hasText("1")
                assertThat(counts.getByLabel("Pending: 1")).hasText("1")
                assertThat(counts.getByLabel("Maintenance: 4")).hasText("4")
            }
        }

        "the header doesn't count the monitors under maintenance as up or pending" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 3, up = 2, inProgress = 1, inMaintenance = 3),
                    inMaintenance = MonitorStateCounts(up = 2, down = 0, pending = 1),
                )
            }

            assertThat(dashboard.statusDetails.getByLabel("Up: 0")).hasText("0")
            assertThat(dashboard.statusDetails.getByLabel("Pending: 0")).hasText("0")
            assertThat(dashboard.statusDetails.getByLabel("Maintenance: 3")).hasText("3")
        }

        listOf(
            "there wasn't any incident yet" to null,
            "there was an incident already" to getCurrentTimestamp().minusHours(2),
        ).forEach { (description, lastIncident) ->

            "the header shows every figure below the verdict, even the zeros, when $description" {
                val dashboard = showDashboard { period ->
                    dashboardOverview(period, dashboardActualStats(total = 1, up = 1, lastIncident = lastIncident))
                }
                val details = dashboard.statusDetails

                assertThat(details.getByLabel("Up: 1")).hasText("1")
                // Only red if any of them is down
                details.getByLabel("Down: 0").shouldBeRed(false)
                assertThat(details.getByLabel("Maintenance: 0")).hasText("0")
                assertThat(details.getByLabel("Paused: 0")).hasText("0")
                assertThat(details.getByLabel("Pending: 0")).hasText("0")
                val lastIncidentLabel = lastIncident?.let { "Last incident: ${it.timeAgo()}" } ?: "No incidents"
                assertThat(details.getByLabel(lastIncidentLabel)).isVisible()
                assertThat(dashboard.moreOngoingIncidentsLink).not().isAttached()
            }
        }

        listOf(
            "there wasn't any incident in the period" to (0 to null) to "-",
            // E.g. a monitor that has just gone down, and the flaps resolved within the same second
            "the incidents of the period were shorter than a second" to (2 to 0L) to "Less than a second",
        ).forEach { (scenario, expectedValue) ->
            val (description, figures) = scenario
            val (incidentCount, meanTimeToResolveSeconds) = figures

            "the downtime and the MTTR are \"$expectedValue\" when $description" {
                val dashboard = showDashboard { period ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 1, up = 1),
                        history = HistoricalUptimeStatsDto(
                            period = period.toString(),
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

                assertThat(dashboard.metricValueOf(dashboard.downtimeCard)).hasText(expectedValue)
                assertThat(dashboard.metricValueOf(dashboard.meanTimeToResolveCard)).hasText(expectedValue)
            }
        }

        "the verdict spells out the invalid certificates, while the figures below it are about the monitors" {
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 2, up = 2),
                    sslStats = SslStats(invalid = 2, valid = 0, willExpire = 1, inProgress = 0),
                )
            }

            assertThat(dashboard.heading).hasText("Invalid certificates: 2")
            assertThat(dashboard.statusDetails).not().containsText("ertificates")
            assertThat(dashboard.statusDetails.getByLabel("Up: 2")).hasText("2")
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
            assertThat(ssl).containsText("started")
            val docker = dashboard.incident("DOCKER Monitor")
            assertThat(docker.getByLabel("Docker")).hasClass(Pattern.compile(".*text-teal-lt-fg.*"))
            assertThat(docker.getByLabel("Docker").locator(".icon-tabler-brand-docker")).hasCount(1)
            // A resolved one tells how long it took, and when it was resolved
            assertThat(docker).containsText("1 hour")
            assertThat(docker).containsText("resolved")
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

            "the certificates card says \"$expectedMessage\" when $description" {
                val dashboard = showDashboard { period ->
                    dashboardOverview(period, dashboardActualStats(total = 2, up = 2), sslStats = sslStats)
                }

                assertThat(dashboard.certificatesCard).containsText(expectedMessage)
                // The pending ones are counted too, if there is any
                val pending = dashboard.certificatesCard.getByLabel("Pending: ${sslStats.inProgress}")
                if (sslStats.inProgress > 0) {
                    assertThat(pending).hasText(sslStats.inProgress.toString())
                } else {
                    assertThat(dashboard.certificatesCard).not().containsText("Pending:")
                }
            }
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

        listOf(
            "monitors are down only outside a maintenance window" to DownCounts(outside = 2, inMaintenance = 0),
            "monitors are down only under maintenance" to DownCounts(outside = 0, inMaintenance = 2),
            "monitors are down both outside and under a maintenance window" to
                DownCounts(outside = 1, inMaintenance = 3),
        ).forEach { (description, counts) ->
            val (outside, inMaintenance) = counts

            "only the ongoing incidents outside maintenance are counted as alarming, when $description" {
                val dashboard = showDashboard { period ->
                    dashboardOverview(
                        period,
                        dashboardActualStats(total = 5, down = outside + inMaintenance, inMaintenance = inMaintenance),
                        inMaintenance = MonitorStateCounts(up = 0, down = inMaintenance, pending = 0),
                        incidents = DashboardIncidentStats(
                            ongoingOutsideMaintenance = outside,
                            ongoingInMaintenance = inMaintenance,
                            resolved = 0,
                            meanTimeToResolveSeconds = null,
                        ),
                    )
                }
                val card = dashboard.incidentsCountCard
                val typeRow = dashboard.monitorTypeRow("push")

                if (outside > 0) {
                    card.getByText("Ongoing: $outside").shouldBeRed()
                    assertThat(typeRow.getByLabel("Down: $outside")).hasText(outside.toString())
                } else {
                    assertThat(card).not().containsText("Ongoing:")
                    assertThat(typeRow).not().containsText("Down:")
                }
                if (inMaintenance > 0) {
                    card.getByText("In maintenance: $inMaintenance").shouldBeRed(false)
                } else {
                    assertThat(card).not().containsText("In maintenance:")
                }
            }
        }

        "every block of the timeline tells its state by its color, and its tooltip doesn't spell out any uptime" {
            val start = OffsetDateTime.parse("2026-09-30T10:00:00Z")
            fun slot(index: Long, uptimeSeconds: Long, downtimeSeconds: Long, incidents: Int) = UptimeTimelineSlot(
                start = start.plusHours(index),
                end = start.plusHours(index + 1),
                uptimeSeconds = uptimeSeconds,
                downtimeSeconds = downtimeSeconds,
                incidents = incidents,
            )
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 1, up = 1),
                    typeTimeline = listOf(
                        slot(index = 0, uptimeSeconds = 0, downtimeSeconds = 0, incidents = 0),
                        slot(index = 1, uptimeSeconds = HOUR, downtimeSeconds = 0, incidents = 0),
                        slot(index = 2, uptimeSeconds = HALF_HOUR, downtimeSeconds = HALF_HOUR, incidents = 1),
                        // An incident that started too late in the slot to add a whole second of downtime yet
                        slot(index = 3, uptimeSeconds = HOUR, downtimeSeconds = 0, incidents = 1),
                    ),
                )
            }
            val blocks = dashboard.monitorTypeRow("push").locator(".tracking-block")

            assertThat(blocks).hasCount(TIMELINE_SLOTS)
            blocks.evaluateAll("blocks => blocks.map(block => block.classList[1])") shouldBe
                listOf("text-muted", "bg-success", "bg-danger", "bg-danger")
            val tooltips = (0 until TIMELINE_SLOTS).map { blocks.nth(it).tooltipText().shouldNotBeNull() }
            tooltips.forEach { it shouldNotContain "uptime" }
            tooltips[0] shouldContain Messages.noData()
            tooltips.drop(1).forEach { it shouldNotContain Messages.noData() }
            tooltips[1] shouldNotContain Messages.incidents()
            tooltips.drop(2).forEach { it shouldContain "${Messages.incidents()}: 1" }
        }

        "the least reliable monitors are listed with their current state and their figures" {
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
                    totalDowntimeSeconds = HOUR,
                ),
            )
            val dashboard = showDashboard { period ->
                dashboardOverview(
                    period,
                    dashboardActualStats(total = 4, up = 2, down = 1, inProgress = 1),
                    leastReliableMonitors = listOf(
                        unreliableMonitor(id = 1, uptimeStatus = UptimeStatus.DOWN),
                        unreliableMonitor(id = 2, inMaintenance = true),
                        unreliableMonitor(id = 3),
                        unreliableMonitor(id = 4, uptimeStatus = null),
                    ),
                )
            }
            val rows = dashboard.leastReliableMonitors
            val expectedStates = listOf(
                "Down" to "status-red",
                "Maintenance" to "status-gray",
                "Up" to "status-green",
                "In Progress" to "status-yellow",
            )

            assertThat(rows).hasCount(expectedStates.size)
            expectedStates.forEachIndexed { index, (label, color) ->
                val row = rows.nth(index)
                assertThat(dashboard.statusDotOf(row)).hasAttribute("aria-label", label)
                assertThat(dashboard.statusDotOf(row)).hasClass(Pattern.compile(".*$color.*"))
                assertThat(row.getByRole(AriaRole.LINK))
                    .hasAttribute("href", "/push-monitors/${index + 1}")
                assertThat(row).containsText("Monitor ${index + 1}")
                assertThat(row.getByLabel("Push")).hasClass(Pattern.compile(".*text-red-lt-fg.*"))
                assertThat(row.getByLabel("50.00% uptime")).isVisible()
                assertThat(row.getByLabel("Incidents: 2")).hasText("2")
                assertThat(row.getByLabel("Downtime: 1 hour")).isVisible()
            }
            // The live state of the down one is pulsing
            assertThat(dashboard.statusDotOf(rows.first())).hasClass(Pattern.compile(".*status-dot-animated.*"))
        }
    }

    private data class DownCounts(val outside: Int, val inMaintenance: Int)

    private companion object {
        const val EVENT_ID_STEP = 10L
        const val CERTIFICATE_MONITOR_ID = 42L
        const val HOUR = 3600L
        const val HALF_HOUR = 1800L
        const val TIMELINE_SLOTS = 4
    }
}
