package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.OffsetDateTime

private val NO_MAINTENANCE = MonitorStateCounts(up = 0, down = 0, pending = 0)
private val NO_CERTIFICATES = SslStats(invalid = 0, valid = 0, willExpire = 0, inProgress = 0)

private fun actual(
    total: Int,
    up: Int = 0,
    down: Int = 0,
    paused: Int = 0,
    inProgress: Int = 0,
    inMaintenance: Int = 0,
) = ActualUptimeStats(
    total = total,
    up = up,
    down = down,
    paused = paused,
    inProgress = inProgress,
    inMaintenance = inMaintenance,
    lastIncident = null,
)

private fun ActualUptimeStats.outsideOf(inMaintenance: MonitorStateCounts) = MonitorStateCounts(
    up = up - inMaintenance.up,
    down = down - inMaintenance.down,
    pending = inProgress - inMaintenance.pending,
)

private fun uptimeStats(
    actual: ActualUptimeStats,
    inMaintenance: MonitorStateCounts = NO_MAINTENANCE,
    sslStats: SslStats = NO_CERTIFICATES,
) = DashboardUptimeStats(
    actual = actual,
    history = HistoricalUptimeStatsDto("PT168H", 0, 0, null, 0),
    incidents = DashboardIncidentStats(0, 0, 0, null),
    outsideMaintenance = actual.outsideOf(inMaintenance),
    inMaintenance = inMaintenance,
    sslStats = sslStats,
    timeline = emptyList(),
    byType = emptyList(),
    certificatesWithIssues = emptyList(),
    monitorsInMaintenance = emptySet(),
    leastReliableMonitors = emptyList(),
)

private fun incident(status: IncidentStatus, startedAt: OffsetDateTime, endedAt: OffsetDateTime?) = IncidentDto(
    monitorId = 1,
    monitorName = "Monitor",
    isMonitorEnabled = true,
    incidentType = IncidentType.HTTP,
    eventId = 1,
    status = status,
    details = null,
    startedAt = startedAt,
    endedAt = endedAt,
    updatedAt = endedAt ?: startedAt,
)

private fun unreliableMonitor(uptimeStatus: UptimeStatus?, inMaintenance: Boolean) = UnreliableMonitor(
    id = NumericMonitorID(MonitorType.PUSH, 1),
    name = "Monitor",
    uptimeStatus = uptimeStatus,
    inMaintenance = inMaintenance,
    history = HistoricalUptimeStatsDto("PT168H", 2, 1, 0.5, 3600),
)

class DashboardOverviewFragmentTest : BehaviorSpec({

    given("the verdict of the dashboard") {

        `when`("there isn't any monitor") {
            then("there is no verdict") {
                uptimeStats(actual(total = 0)).verdict() shouldBe null
            }
        }

        listOf(
            "the paused monitors are left out of the count of the down ones" to
                uptimeStats(actual(total = 4, up = 1, down = 1, paused = 2)) to
                Verdict("Monitors down: 1 of 2", STATUS_RED),
            "a monitor is down outside a maintenance window, and another one within one" to
                uptimeStats(
                    actual(total = 3, up = 1, down = 2, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                ) to Verdict("Monitors down: 1 of 3", STATUS_RED),
            "a monitor is down, while certificates are invalid too" to
                uptimeStats(
                    actual(total = 2, up = 1, down = 1),
                    sslStats = NO_CERTIFICATES.copy(invalid = 1),
                ) to Verdict("Monitors down: 1 of 2", STATUS_RED),
            "certificates are invalid, and others expire soon" to
                uptimeStats(
                    actual(total = 2, up = 2),
                    sslStats = NO_CERTIFICATES.copy(invalid = 2, willExpire = 1),
                ) to
                Verdict("Invalid certificates: 2", STATUS_RED),
            "certificates expire soon" to
                uptimeStats(actual(total = 2, up = 2), sslStats = NO_CERTIFICATES.copy(willExpire = 1)) to
                Verdict("Certificates expiring soon", STATUS_YELLOW),
            "the only monitor that is down is under maintenance" to
                uptimeStats(
                    actual(total = 3, up = 2, down = 1, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 1, pending = 0),
                ) to Verdict("Down during maintenance: 1", STATUS_SECONDARY),
            "every monitor that isn't paused is waiting for its first check" to
                uptimeStats(actual(total = 2, paused = 1, inProgress = 1)) to
                Verdict("Waiting for the first checks", STATUS_YELLOW),
            "every monitor that isn't paused is under maintenance, and none of them is down" to
                uptimeStats(
                    actual(total = 3, up = 2, inProgress = 1, inMaintenance = 3),
                    inMaintenance = MonitorStateCounts(up = 2, down = 0, pending = 1),
                ) to Verdict("In maintenance: 3", STATUS_SECONDARY),
            "the only monitor that isn't paused is pending under maintenance" to
                uptimeStats(
                    actual(total = 2, paused = 1, inProgress = 1, inMaintenance = 1),
                    inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 1),
                ) to Verdict("In maintenance: 1", STATUS_SECONDARY),
            "some monitors are up outside maintenance, while the rest of them are pending under maintenance" to
                uptimeStats(
                    actual(total = 3, up = 1, inProgress = 2, inMaintenance = 2),
                    inMaintenance = MonitorStateCounts(up = 0, down = 0, pending = 2),
                ) to Verdict("All systems operational", STATUS_GREEN),
            "every monitor is paused" to
                uptimeStats(actual(total = 2, paused = 2)) to
                Verdict("Every monitor is paused", STATUS_CYAN),
            "some of the monitors are up, and the rest of them are pending or paused" to
                uptimeStats(actual(total = 3, up = 1, paused = 1, inProgress = 1)) to
                Verdict("All systems operational", STATUS_GREEN),
        ).forEach { (scenario, expectedVerdict) ->
            val (description, stats) = scenario

            `when`(description) {
                then("the verdict is \"${expectedVerdict.title}\"") {
                    stats.verdict() shouldBe expectedVerdict
                }
            }
        }
    }

    given("the monitor counts") {

        `when`("monitors of every state are under maintenance") {
            val stats = actual(total = 9, up = 4, down = 2, paused = 1, inProgress = 2, inMaintenance = 4)
            val outsideMaintenance = stats.outsideOf(MonitorStateCounts(up = 2, down = 1, pending = 1))

            then("every monitor is counted once, the ones under maintenance only by the maintenance") {
                monitorCountsOf(stats, outsideMaintenance, withZeros = false) shouldBe listOf(
                    CountFigure(Icon.ARROW_NARROW_UP, "Up: 2", 2, hiddenOnSmallScreens = true),
                    CountFigure(Icon.ARROW_NARROW_DOWN, "Down: 1", 1, TEXT_RED),
                    CountFigure(Icon.TOOL, "Maintenance: 4", 4),
                    CountFigure(Icon.PAUSE, "Paused: 1", 1, hiddenOnSmallScreens = true),
                    CountFigure(Icon.HOURGLASS, "Pending: 1", 1, hiddenOnSmallScreens = true),
                )
            }
        }

        `when`("only some of the states have monitors") {
            val stats = actual(total = 1, up = 1)

            then("the zeros are left out, except for the monitors that are up") {
                monitorCountsOf(stats, stats.outsideOf(NO_MAINTENANCE), withZeros = false) shouldBe listOf(
                    CountFigure(Icon.ARROW_NARROW_UP, "Up: 1", 1, hiddenOnSmallScreens = true),
                )
            }

            then("every count is there when the zeros are asked for, and nothing is red") {
                monitorCountsOf(stats, stats.outsideOf(NO_MAINTENANCE), withZeros = true) shouldBe listOf(
                    CountFigure(Icon.ARROW_NARROW_UP, "Up: 1", 1, hiddenOnSmallScreens = true),
                    CountFigure(Icon.ARROW_NARROW_DOWN, "Down: 0", 0),
                    CountFigure(Icon.TOOL, "Maintenance: 0", 0),
                    CountFigure(Icon.PAUSE, "Paused: 0", 0, hiddenOnSmallScreens = true),
                    CountFigure(Icon.HOURGLASS, "Pending: 0", 0, hiddenOnSmallScreens = true),
                )
            }
        }
    }

    given("the certificate counts") {

        `when`("there are certificates in every state") {
            val sslStats = SslStats(invalid = 1, valid = 3, willExpire = 2, inProgress = 4)

            then("every count is there, the problematic ones in color") {
                certificateCountsOf(sslStats) shouldBe listOf(
                    CountFigure(Icon.LOCK_CLOSED, "Valid: 3", 3),
                    CountFigure(Icon.TIMER, "Expires soon: 2", 2, TEXT_YELLOW),
                    CountFigure(Icon.LOCK_OPEN, "Invalid: 1", 1, TEXT_RED),
                    CountFigure(Icon.HOURGLASS, "Pending: 4", 4),
                )
            }
        }

        `when`("there are no certificates at all") {

            then("only the valid ones are counted") {
                certificateCountsOf(NO_CERTIFICATES) shouldBe listOf(CountFigure(Icon.LOCK_CLOSED, "Valid: 0", 0))
            }
        }
    }

    given("a measured interval of the dashboard") {
        listOf(
            null to "-",
            0L to "Less than a second",
            3600L to "1 hour",
        ).forEach { (seconds, expected) ->
            `when`("it's $seconds seconds") {
                then("it's shown as \"$expected\"") {
                    seconds.formatAsIntervalOrDash() shouldBe expected
                }
            }
        }
    }

    given("a block of the uptime timeline") {
        val start = OffsetDateTime.parse("2026-09-30T10:00:00Z")
        fun slot(uptimeSeconds: Long, downtimeSeconds: Long, incidents: Int) = UptimeTimelineSlot(
            start = start,
            end = start.plusHours(1),
            uptimeSeconds = uptimeSeconds,
            downtimeSeconds = downtimeSeconds,
            incidents = incidents,
        )

        listOf(
            "it has no data" to
                slot(uptimeSeconds = 0, downtimeSeconds = 0, incidents = 0) to
                (TEXT_MUTED to listOf("N/A")),
            "the monitor was up all along" to
                slot(uptimeSeconds = 3600, downtimeSeconds = 0, incidents = 0) to
                (BG_SUCCESS to emptyList()),
            "the monitor was down for a while" to
                slot(uptimeSeconds = 1800, downtimeSeconds = 1800, incidents = 1) to
                (BG_DANGER to listOf("Incidents: 1")),
            "an incident started at its very end" to
                slot(uptimeSeconds = 3600, downtimeSeconds = 0, incidents = 1) to
                (BG_DANGER to listOf("Incidents: 1")),
        ).forEach { (scenario, expected) ->
            val (description, slot) = scenario
            val (expectedColor, expectedTooltipParts) = expected

            `when`(description) {
                then("its color is $expectedColor, and its tooltip never spells out the uptime") {
                    slot.blockColor shouldBe expectedColor
                    slot.blockTooltip.split(" · ").drop(1) shouldBe expectedTooltipParts
                }
            }
        }
    }

    given("a recent incident on the dashboard") {
        val now = OffsetDateTime.now()

        val ongoingState = RowState(setOf(STATUS_RED, STATUS_DOT_ANIMATED), "Ongoing")
        val resolvedState = RowState(setOf(STATUS_GREEN), "Resolved")
        listOf(
            "it's ongoing" to (IncidentStatus.ONGOING to false) to ongoingState,
            "it's ongoing, under maintenance" to (IncidentStatus.ONGOING to true) to
                RowState(setOf(STATUS_SECONDARY), "Maintenance"),
            "it's resolved" to (IncidentStatus.RESOLVED to false) to resolvedState,
            // A resolved incident isn't alarming anymore, whether its monitor is under maintenance or not
            "it's resolved, under maintenance" to (IncidentStatus.RESOLVED to true) to resolvedState,
        ).forEach { (scenario, expectedState) ->
            val (description, state) = scenario
            val (status, inMaintenance) = state

            `when`(description) {
                then("its state is \"${expectedState.label}\"") {
                    val endedAt = now.minusHours(1).takeIf { status == IncidentStatus.RESOLVED }
                    incident(status, now.minusHours(2), endedAt).dashboardRowState(inMaintenance) shouldBe expectedState
                }
            }
        }

        `when`("it's ongoing") {
            val ongoing = incident(IncidentStatus.ONGOING, now.minusHours(2), endedAt = null)

            then("it tells when it started") {
                ongoing.dashboardDetails shouldBe listOf("started ${now.minusHours(2).timeAgo()}")
            }
        }

        `when`("it's resolved") {
            val resolvedAt = now.minusHours(1)
            val resolved = incident(IncidentStatus.RESOLVED, now.minusHours(2), resolvedAt)

            then("it tells how long it took, and when it was resolved") {
                resolved.dashboardDetails shouldBe listOf("1 hour", "resolved ${resolvedAt.timeAgo()}")
            }
        }
    }

    given("the certificates card without any certificate with issues") {
        listOf(
            "none of the certificates has been checked yet" to
                NO_CERTIFICATES.copy(inProgress = 2) to "Waiting for the first certificate checks",
            "some of the certificates haven't been checked yet" to
                NO_CERTIFICATES.copy(valid = 1, inProgress = 1) to "Every checked certificate is valid",
            "every certificate is checked" to
                NO_CERTIFICATES.copy(valid = 2) to "Every certificate is valid",
        ).forEach { (scenario, expectedMessage) ->
            val (description, sslStats) = scenario

            `when`(description) {
                then("it says \"$expectedMessage\"") {
                    sslStats.certificatesMessage shouldBe expectedMessage
                }
            }
        }
    }

    given("a least reliable monitor") {
        listOf(
            "it's down" to unreliableMonitor(UptimeStatus.DOWN, inMaintenance = false) to
                RowState(setOf(STATUS_RED, STATUS_DOT_ANIMATED), "Down"),
            // The maintenance wins, as it's not alarming then
            "it's down under maintenance" to unreliableMonitor(UptimeStatus.DOWN, inMaintenance = true) to
                RowState(setOf(STATUS_SECONDARY), "Maintenance"),
            "it's up" to unreliableMonitor(UptimeStatus.UP, inMaintenance = false) to
                RowState(setOf(STATUS_GREEN), "Up"),
            "it hasn't been checked yet" to unreliableMonitor(uptimeStatus = null, inMaintenance = false) to
                RowState(setOf(STATUS_YELLOW), "In Progress"),
        ).forEach { (scenario, expectedState) ->
            val (description, monitor) = scenario

            `when`(description) {
                then("its state is \"${expectedState.label}\"") {
                    monitor.dashboardRowState shouldBe expectedState
                }
            }
        }
    }

    given("the label of a period") {
        listOf(
            "PT1H" to "Last hour",
            "PT6H" to "Last 6 hours",
            "PT12H" to "Last 12 hours",
            "PT24H" to "Last 24 hours",
            "PT168H" to "Last 7 days",
            "PT720H" to "Last 30 days",
            "PT48H" to "2 days",
        ).forEach { (period, expectedLabel) ->
            `when`("the period is $period") {
                then("it's \"$expectedLabel\"") {
                    Duration.parse(period).lastPeriodLabel() shouldBe expectedLabel
                }
            }
        }
    }
})
