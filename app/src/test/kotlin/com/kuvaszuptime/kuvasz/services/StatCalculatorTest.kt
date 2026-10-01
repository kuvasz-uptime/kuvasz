package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorSummary
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.monitorType
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.testutils.shouldEqualRounded
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

@MicronautTest(startApplication = false)
class StatCalculatorTest(
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    icmpMonitorRepository: IcmpMonitorRepository,
    tcpMonitorRepository: TcpMonitorRepository,
    dnsMonitorRepository: DnsMonitorRepository,
    dockerMonitorRepository: DockerMonitorRepository,
    monitorRepositories: List<MonitorRepository<*, *>>,
    uptimeEventRepositories: List<UptimeEventRepository>,
    statCalculator: StatCalculator,
    maintenanceWindowActions: MaintenanceWindowActions,
) : DatabaseBehaviorSpec() {
    init {

        // The dashboard loads the maintenance windows once, and hands them over to the calculator
        fun dashboardStats(period: Duration): DashboardUptimeStats =
            statCalculator.calculateDashboardUptimeStats(period, maintenanceWindowActions.getMaintenanceWindows())

        given("the repositories the calculator is relying on") {

            `when`("the beans of the different monitor types are collected") {

                then("there should be a monitor and an uptime event repository for every type") {
                    monitorRepositories.map { it.monitorType } shouldContainExactlyInAnyOrder
                        MonitorType.entries
                    uptimeEventRepositories.map { it.monitorType } shouldContainExactlyInAnyOrder
                        MonitorType.entries
                }
            }
        }

        given("the calculateOverallHttpStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val enabledDownMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val pausedMonitor = createHttpMonitor(httpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    status = SslStatus.INVALID,
                    startedAt = now.minusDays(10),
                    endedAt = null,
                )
                // enabledDownMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = now.minusDays(10),
                    endedAt = null,
                )
                // pausedMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = now.minusDays(2),
                    endedAt = null,
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.actual.sslStats.valid shouldBe 1
                    stats.actual.sslStats.invalid shouldBe 1
                    stats.actual.sslStats.willExpire shouldBe 0
                    stats.actual.sslStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val enabledDownMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val pausedMonitor = createHttpMonitor(httpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    status = SslStatus.INVALID,
                    startedAt = now.minusDays(10),
                    endedAt = null,
                )
                // enabledDownMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = now.minusDays(10),
                    endedAt = null,
                )
                // pausedMonitor's incidents
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = now.minusDays(10),
                    endedAt = null,
                    updatedAt = now.minusDays(7)
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.actual.sslStats.valid shouldBe 1
                    stats.actual.sslStats.invalid shouldBe 1
                    stats.actual.sslStats.willExpire shouldBe 0
                    stats.actual.sslStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
                val oldMonitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)

                // Old monitor's events
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1
                    stats.actual.sslStats.valid shouldBe 1
                    stats.actual.sslStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("there are events outside of the given time period") {

                val monitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)

                // Events outside the 9 days period
                val firstUptimeEvent = createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(12),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(10),
                )
                // Event that overlaps with the start of the 9 days period, downtime should be counted from the
                // beginning of the given period
                val secondUptimeEvent = createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = firstUptimeEvent.endedAt.shouldNotBeNull(),
                    status = UptimeStatus.DOWN,
                    endedAt = getCurrentTimestamp().minusDays(7),
                )
                // Events within the 9 days period
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = secondUptimeEvent.endedAt.shouldNotBeNull(),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(9))

                then("historical data should only contain events within the period") {
                    stats.actual.uptimeStats.total shouldBe 1
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.actual.sslStats.valid shouldBe 0
                    stats.actual.sslStats.invalid shouldBe 1
                    stats.actual.sslStats.willExpire shouldBe 0
                    stats.actual.sslStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 1
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    // The uptime ratio calculation should only take the time within the given period into account
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 7.toDouble() / 9
                    // 2 days in seconds, because even the downtime stared before the period, only the part within the
                    // period should be counted
                    val expectedDowntimeSeconds = 2L * 24 * 60 * 60
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds - 1..expectedDowntimeSeconds + 1
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                val upMonitorInProgress =
                    createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
                val upMonitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = false)
                val downMonitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
                val pausedMonitor = createHttpMonitor(httpMonitorRepository, enabled = false, sslCheckEnabled = true)
                val validSSLMonitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
                createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true) // sslInProgressMonitor

                // upMonitorInProgress's events: in progress UPTIME check + INVALID SSL
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitorInProgress.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = null,
                )

                // upMonitor's events: UP + waiting for SSL check (should not be counted, because of disabled SSL check)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN + WILL_EXPIRE SSL
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    status = SslStatus.WILL_EXPIRE,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    endedAt = null,
                )

                // pausedMonitor's events: UP + VALID SSL (but they should not be counted)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                )

                // validSSLMonitor's events: UP + VALID SSL
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = validSSLMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = validSSLMonitor.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = null,
                )

                // sslInProgressMonitor's has no events at all

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 6
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 2
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 2

                    stats.actual.sslStats.valid shouldBe 1
                    stats.actual.sslStats.invalid shouldBe 1
                    stats.actual.sslStats.willExpire shouldBe 1
                    stats.actual.sslStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.actual.sslStats.valid shouldBe 0
                    stats.actual.sslStats.invalid shouldBe 0
                    stats.actual.sslStats.willExpire shouldBe 0
                    stats.actual.sslStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createHttpMonitor(httpMonitorRepository)
                val monitor2 = createHttpMonitor(httpMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds - 1..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                createHttpMonitor(httpMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-http-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }

            `when`("a monitor is covered by a window through its category") {

                createHttpMonitor(httpMonitorRepository, enabled = true, category = "Payments")
                createHttpMonitor(httpMonitorRepository, enabled = true, category = "Search")
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-category-window",
                    enabled = true,
                    categories = listOf("Payments"),
                )

                then("only that one is counted as under maintenance on the dashboard") {
                    val stats = statCalculator.calculateOverallHttpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateOverallPushStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val enabledDownMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val pausedMonitor = createPushMonitor(pushMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val enabledDownMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val pausedMonitor = createPushMonitor(pushMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )

                // pausedMonitor's incidents
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createPushMonitor(pushMonitorRepository, enabled = true)
                val oldMonitor = createPushMonitor(pushMonitorRepository, enabled = true)

                // Old monitor's events
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("there are events outside of the given time period") {

                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)

                // Events outside the 9 days period
                val firstUptimeEvent = createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(12),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(10),
                )
                // Event that overlaps with the start of the 9 days period, downtime should be counted from the
                // beginning of the given period
                val secondUptimeEvent = createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = firstUptimeEvent.endedAt.shouldNotBeNull(),
                    status = UptimeStatus.DOWN,
                    endedAt = getCurrentTimestamp().minusDays(7),
                )
                // Events within the 9 days period
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = secondUptimeEvent.endedAt.shouldNotBeNull(),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(9))

                then("historical data should only contain events within the period") {
                    stats.actual.uptimeStats.total shouldBe 1
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 1
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    // The uptime ratio calculation should only take the time within the given period into account
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 7.toDouble() / 9
                    // 2 days in seconds, because even the downtime stared before the period, only the part within the
                    // period should be counted
                    val expectedDowntimeSeconds = 2L * 24 * 60 * 60
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds - 1..expectedDowntimeSeconds + 1
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                createPushMonitor(pushMonitorRepository, enabled = true)
                val upMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val downMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val pausedMonitor = createPushMonitor(pushMonitorRepository, enabled = false)

                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 4
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createPushMonitor(pushMonitorRepository)
                val monitor2 = createPushMonitor(pushMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }
        }

        given("the calculateHistoricalUptimeStats(MonitorType.HTTP_SSL, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createHttpMonitor(httpMonitorRepository, enabled = true)
                val upMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val downMonitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val pausedMonitor = createHttpMonitor(httpMonitorRepository, enabled = false)
                val pausedMonitor2 = createHttpMonitor(httpMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }
        }

        given("the calculateHistoricalUptimeStats(MonitorType.PUSH, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createPushMonitor(pushMonitorRepository, enabled = true)
                val upMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val downMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val pausedMonitor = createPushMonitor(pushMonitorRepository, enabled = false)
                val pausedMonitor2 = createPushMonitor(pushMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                createPushMonitor(pushMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-push-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.PUSH, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallPushStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateOverallIcmpStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val enabledDownMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val pausedMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val enabledDownMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val pausedMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val oldMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)

                // Old monitor's events
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                createIcmpMonitor(icmpMonitorRepository, enabled = true) // inProgressMonitor
                val upMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val downMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val pausedMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)

                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 4
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createIcmpMonitor(icmpMonitorRepository)
                val monitor2 = createIcmpMonitor(icmpMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                createIcmpMonitor(icmpMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-icmp-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.ICMP, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallIcmpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateHistoricalUptimeStats(MonitorType.ICMP, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val upMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val downMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val pausedMonitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                val pausedMonitor2 = createIcmpMonitor(icmpMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }
        }

        given("the calculateOverallTcpStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val enabledDownMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val pausedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val enabledDownMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val pausedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createTcpMonitor(tcpMonitorRepository, enabled = true)
                val oldMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)

                // Old monitor's events
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                createTcpMonitor(tcpMonitorRepository, enabled = true) // inProgressMonitor
                val upMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val downMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val pausedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = false)

                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 4
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createTcpMonitor(tcpMonitorRepository)
                val monitor2 = createTcpMonitor(tcpMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                createTcpMonitor(tcpMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-tcp-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.TCP, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallTcpStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateOverallDnsStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val enabledDownMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val pausedMonitor = createDnsMonitor(dnsMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val enabledDownMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val pausedMonitor = createDnsMonitor(dnsMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createDnsMonitor(dnsMonitorRepository, enabled = true)
                val oldMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)

                // Old monitor's events
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                createDnsMonitor(dnsMonitorRepository, enabled = true) // inProgressMonitor
                val upMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val downMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val pausedMonitor = createDnsMonitor(dnsMonitorRepository, enabled = false)

                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 4
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createDnsMonitor(dnsMonitorRepository)
                val monitor2 = createDnsMonitor(dnsMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                createDnsMonitor(dnsMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-dns-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DNS, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallDnsStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateHistoricalUptimeStats(MonitorType.TCP, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val upMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val downMonitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val pausedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = false)
                val pausedMonitor2 = createTcpMonitor(tcpMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }
        }


        given("the calculateHistoricalUptimeStats(MonitorType.DNS, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val upMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val downMonitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val pausedMonitor = createDnsMonitor(dnsMonitorRepository, enabled = false)
                val pausedMonitor2 = createDnsMonitor(dnsMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }
        }

        given("the calculateOverallDockerStats method") {

            `when`("there is a paused monitor") {

                val enabledUpMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val enabledDownMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val pausedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(1), // 1 day DOWN
                )

                then("it should count their uptime based on the events' update date in the statistics") {
                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 3
                    stats.history.uptimeStats.affectedMonitors shouldBe 3
                    // 2.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 60 * 60 * 60
                    // 5 days UP, 2.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 7.5
                }
            }

            `when`("there is a paused monitor - last update before the period") {

                val enabledUpMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val enabledDownMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val pausedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = false)
                val now = getCurrentTimestamp()

                // enabledUpMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(5), // 5 days DOWN, 1 day in the period
                )
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledUpMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.UP,
                    endedAt = null, // 5 days UP
                )
                // enabledDownMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = enabledDownMonitor.id,
                    startedAt = now.minusHours(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null, // 0.5 day DOWN
                )
                // pausedMonitor's incidents
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(12),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(8),
                )

                then("it should not count the obsolete events from the paused monitor") {
                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 3 // 2 enabled monitors + 1 paused monitor
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 0
                    stats.actual.uptimeStats.inMaintenance shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 2
                    stats.history.uptimeStats.affectedMonitors shouldBe 2
                    // 1.5 days DOWN inside the period
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 36 * 60 * 60
                    // 5 days UP, 1.5 days DOWN
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 5.toDouble() / 6.5
                }
            }

            `when`("there is a monitor that was just created") {

                createDockerMonitor(dockerMonitorRepository, enabled = true)
                val oldMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)

                // Old monitor's events
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = oldMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should count it as an in progress one") {

                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2 // 1 old monitor + 1 new monitor
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                }
            }

            `when`("monitors with all the exposed statuses are present") {

                createDockerMonitor(dockerMonitorRepository, enabled = true) // inProgressMonitor
                val upMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val downMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val pausedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = false)

                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                then("it should correctly calculate the stats for all statuses") {
                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 4
                    stats.actual.uptimeStats.down shouldBe 1
                    stats.actual.uptimeStats.up shouldBe 1
                    stats.actual.uptimeStats.paused shouldBe 1
                    stats.actual.uptimeStats.inProgress shouldBe 1

                    stats.history.uptimeStats.incidents shouldBe 1 // Only the downMonitor has an incident
                    stats.history.uptimeStats.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                }
            }

            `when`("there are no events in the given period") {

                val monitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
                )

                val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))

                then("it should handle it gracefully and return null as the ratio") {

                    stats.history.uptimeStats.uptimeRatio shouldBe null
                }
            }

            `when`("there are no monitors at all") {

                then("it should return empty stats") {
                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))

                    stats.actual.uptimeStats.total shouldBe 0
                    stats.actual.uptimeStats.down shouldBe 0
                    stats.actual.uptimeStats.up shouldBe 0
                    stats.actual.uptimeStats.paused shouldBe 0
                    stats.actual.uptimeStats.inProgress shouldBe 0

                    stats.history.uptimeStats.incidents shouldBe 0
                    stats.history.uptimeStats.affectedMonitors shouldBe 0
                    stats.history.uptimeStats.uptimeRatio shouldBe null
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBe 0L
                }
            }

            `when`("there are multiple events for a given period") {

                val monitor1 = createDockerMonitor(dockerMonitorRepository)
                val monitor2 = createDockerMonitor(dockerMonitorRepository)

                val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
                val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = firstUpStartedAt,
                    endedAt = firstUpEndedAt,
                )
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor1.id,
                    startedAt = firstUpEndedAt,
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
                val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = secondDownStartedAt,
                    endedAt = secondDownEndedAt,
                )
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.UP,
                    startedAt = secondDownEndedAt,
                    endedAt = null,
                )

                val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(12))

                then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                    // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                    stats.history.uptimeStats.uptimeRatio shouldEqualRounded 6.toDouble() / 13
                    // 5 days + 2 days in seconds
                    val expectedDowntimeSeconds = 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60L
                    stats.history.uptimeStats.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
                }
            }

            `when`("there is a monitor under an active maintenance window") {

                val maintainedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                createDockerMonitor(dockerMonitorRepository, enabled = true)
                createMaintenanceWindow(
                    dslContext = dslContext,
                    name = "active-docker-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, maintainedMonitor.name)),
                )

                then("it should count it as under maintenance") {
                    val stats = statCalculator.calculateOverallDockerStats(Duration.ofDays(6))
                    stats.actual.uptimeStats.total shouldBe 2
                    stats.actual.uptimeStats.inMaintenance shouldBe 1
                }
            }
        }

        given("the calculateHistoricalUptimeStats(MonitorType.DOCKER, monitor) method") {

            `when`("monitors with all the exposed statuses are present") {

                val now = getCurrentTimestamp()
                val upMonitorInProgress = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val upMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val downMonitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val pausedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = false)
                val pausedMonitor2 = createDockerMonitor(dockerMonitorRepository, enabled = false)

                // upMonitor's events: UP
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                // downMonitor's events: DOWN
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now.minusDays(5),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                )

                // pausedMonitor's events: UP (it should be counted until it's update date, because it's ongoing)
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(2),
                    status = UptimeStatus.UP,
                    endedAt = null,
                    updatedAt = now.minusDays(1),
                )
                // pausedMonitor's events: DOWN (it should be counted until it's end date)
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    startedAt = now.minusDays(3),
                    status = UptimeStatus.DOWN,
                    endedAt = now.minusDays(2),
                )

                // pausedMonitor2's events: DOWN, but update date is before the period, so it should not be counted
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor2.id,
                    startedAt = now.minusDays(10),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    updatedAt = now.minusDays(7),
                )

                then("it should correctly calculate the stats for all statuses") {
                    val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(6),
                        monitorId = upMonitorInProgress.id,
                    )
                    statsOfInProgressUpMonitor.incidents shouldBe 0
                    statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                    statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                    val statsOfUpMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(6),
                        monitorId = upMonitor.id,
                    )
                    statsOfUpMonitor.incidents shouldBe 0
                    statsOfUpMonitor.affectedMonitors shouldBe 0
                    statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                    statsOfUpMonitor.uptimeRatio shouldBe 1.0

                    val statsOfDownMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(6),
                        monitorId = downMonitor.id,
                    )
                    statsOfDownMonitor.incidents shouldBe 1
                    statsOfDownMonitor.affectedMonitors shouldBe 1
                    val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                    statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                    statsOfDownMonitor.uptimeRatio shouldBe 0.0

                    val statsOfPausedMonitor = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor.id,
                    )
                    statsOfPausedMonitor.incidents shouldBe 1
                    statsOfPausedMonitor.affectedMonitors shouldBe 1
                    statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                    statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                    val statsOfPausedMonitor2 = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(6),
                        monitorId = pausedMonitor2.id,
                    )
                    statsOfPausedMonitor2.incidents shouldBe 0
                    statsOfPausedMonitor2.affectedMonitors shouldBe 0
                    statsOfPausedMonitor2.totalDowntimeSeconds shouldBe 0
                    statsOfPausedMonitor2.uptimeRatio shouldBe null
                }
            }
        }

        given("the calculateUptimeOverviews() method") {

            fun createTestEvent(
                monitorId: Long,
                status: UptimeStatus,
                startedAt: OffsetDateTime,
                endedAt: OffsetDateTime? = null,
                updatedAt: OffsetDateTime,
            ) = createPushUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitorId,
                status = status,
                startedAt = startedAt,
                endedAt = endedAt,
                updatedAt = updatedAt,
            )

            fun statusHistoryOf(monitorId: Long, period: Duration) = statCalculator
                .calculateUptimeOverviews(MonitorType.PUSH, period, listOf(monitorId))
                .getValue(monitorId)
                .statusHistory

            `when`("there is no event for a given day") {

                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(9),
                    endedAt = getCurrentTimestamp().minusDays(8),
                    updatedAt = getCurrentTimestamp().minusDays(8),
                )
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(8),
                    endedAt = getCurrentTimestamp().minusDays(7),
                    updatedAt = getCurrentTimestamp().minusDays(7),
                )

                then("it should return null for that day as outageCnt") {

                    val result = statusHistoryOf(monitor.id, Duration.ofDays(11))

                    result shouldBeSortedBy { it.date }
                    result shouldHaveSize 11
                    // First event is 9 days ago, so the first day should have null as outageCnt
                    with(result.first()) {
                        date shouldBe getCurrentTimestamp().minusDays(10).toLocalDate()
                        outageCnt shouldBe null
                    }
                    // First effective event is 9 days ago with UP status, so the second day should have 0 as outageCnt
                    with(result[1]) {
                        date shouldBe getCurrentTimestamp().minusDays(9).toLocalDate()
                        outageCnt shouldBe 0
                    }
                    // Second effective event is 8 days ago with DOWN status, so the third day should have 1 as
                    // outageCnt
                    with(result[2]) {
                        date shouldBe getCurrentTimestamp().minusDays(8).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // The previous down event was still effective on the 4th day, so it should also have 1 as outageCnt
                    with(result[3]) {
                        date shouldBe getCurrentTimestamp().minusDays(7).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // For the rest of the days there are no more events, so they should have null as outageCnt
                    result.filter { it.date > result[3].date }.forAll { daysWithNoData ->
                        daysWithNoData.outageCnt shouldBe null
                    }
                }
            }

            `when`("there is an event that started before the period, but ended within it") {

                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(3),
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp(),
                )

                then("it should count that event on the days within the period") {

                    val result = statusHistoryOf(monitor.id, Duration.ofDays(7))

                    result shouldBeSortedBy { it.date }
                    result shouldHaveSize 7
                    // First effective event is 6 days ago with DOWN status, so the first day should have 1 as outageCnt
                    with(result.first()) {
                        date shouldBe getCurrentTimestamp().minusDays(6).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // The previous down event was still effective on the 2nd, 3rd and 4th days, so they should also
                    // have 1 as outageCnt
                    with(result[1]) {
                        date shouldBe getCurrentTimestamp().minusDays(5).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    with(result[2]) {
                        date shouldBe getCurrentTimestamp().minusDays(4).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    with(result[3]) {
                        date shouldBe getCurrentTimestamp().minusDays(3).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // From the 5th day the UP event is effective, so it should have 0 as outageCnt
                    result.filter { it.date > result[3].date }.forAll { daysWithUpEvent ->
                        daysWithUpEvent.outageCnt shouldBe 0
                    }
                }
            }

            `when`("an open event was updated before today") {

                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(3),
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createTestEvent(
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(1),
                )

                then("its updateDate should be the base of the calculation") {

                    val result = statusHistoryOf(monitor.id, Duration.ofDays(7))
                    val today = getCurrentTimestamp().toLocalDate()

                    result shouldBeSortedBy { it.date }
                    result shouldHaveSize 7
                    // First effective event is 6 days ago with DOWN status, so the first day should have 1 as outageCnt
                    with(result.first()) {
                        date shouldBe getCurrentTimestamp().minusDays(6).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // The previous down event was still effective on the 2nd, 3rd and 4th days, so they should also
                    // have 1 as outageCnt
                    with(result[1]) {
                        date shouldBe getCurrentTimestamp().minusDays(5).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    with(result[2]) {
                        date shouldBe getCurrentTimestamp().minusDays(4).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    with(result[3]) {
                        date shouldBe getCurrentTimestamp().minusDays(3).toLocalDate()
                        outageCnt shouldBe 1
                    }
                    // From the 5th day the UP event is effective, so it should have 0 as outageCnt
                    result.filter { it.date > result[3].date && it.date < today }.forAll { daysWithUpEvent ->
                        daysWithUpEvent.outageCnt shouldBe 0
                    }

                    // The last day is today, but the event was updated yesterday, so it should have null as outageCnt
                    result.last().date shouldBe today
                    result.last().outageCnt shouldBe null
                }
            }

            `when`("the overviews of multiple monitors are requested at once") {

                val period = Duration.ofDays(7)
                val downMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val upMonitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val monitorWithoutEvents = createPushMonitor(pushMonitorRepository, enabled = true)
                createTestEvent(
                    monitorId = downMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp(),
                )
                createTestEvent(
                    monitorId = upMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp(),
                )

                then("every requested monitor should get its own overview, from a single fetch") {

                    val result = statCalculator.calculateUptimeOverviews(
                        monitorType = MonitorType.PUSH,
                        period = period,
                        monitorIds = listOf(downMonitor.id, upMonitor.id, monitorWithoutEvents.id),
                    )

                    result.keys shouldContainExactlyInAnyOrder
                        listOf(downMonitor.id, upMonitor.id, monitorWithoutEvents.id)

                    // The events of the monitors should not leak into each other's overview
                    result.getValue(downMonitor.id).uptimeRatio shouldBe 0.0
                    result.getValue(downMonitor.id).statusHistory.last().outageCnt shouldBe 1
                    result.getValue(upMonitor.id).uptimeRatio shouldBe 1.0
                    result.getValue(upMonitor.id).statusHistory.last().outageCnt shouldBe 0

                    // A monitor without any event in the period still gets an entry, with nothing measured
                    with(result.getValue(monitorWithoutEvents.id)) {
                        uptimeRatio shouldBe null
                        statusHistory shouldHaveSize 7
                        statusHistory.forAll { it.outageCnt shouldBe null }
                    }
                }
            }

            `when`("no monitor is requested") {

                then("it should not even hit the database") {

                    statCalculator.calculateUptimeOverviews(
                        monitorType = MonitorType.PUSH,
                        period = Duration.ofDays(7),
                        monitorIds = emptyList(),
                    ) shouldBe emptyMap()
                }
            }
        }

        given("the calculateDashboardUptimeStats() method") {

            `when`("there isn't any monitor") {

                val stats = dashboardStats(Duration.ofDays(7))

                then("it should return empty figures, but a full timeline of the period") {
                    stats.actual.total shouldBe 0
                    stats.actual.lastIncident shouldBe null
                    stats.history.uptimeRatio shouldBe null
                    stats.history.affectedMonitors shouldBe 0
                    stats.incidents shouldBe
                        DashboardIncidentStats(
                            ongoingOutsideMaintenance = 0,
                            ongoingInMaintenance = 0,
                            resolved = 0,
                            meanTimeToResolveSeconds = null,
                        )
                    stats.outsideMaintenance shouldBe MonitorStateCounts(up = 0, down = 0, pending = 0)
                    stats.inMaintenance shouldBe MonitorStateCounts(up = 0, down = 0, pending = 0)
                    stats.byType.shouldBeEmpty()
                    stats.certificatesWithIssues.shouldBeEmpty()
                    stats.monitorsInMaintenance.shouldBeEmpty()
                    stats.leastReliableMonitors.shouldBeEmpty()
                    // 6-hour slots, the first and the current one being partial, unless the period starts on a boundary
                    stats.timeline.size shouldBeInRange 28..29
                    stats.timeline.forAll { slot ->
                        slot.uptimeSeconds shouldBe 0
                        slot.downtimeSeconds shouldBe 0
                        slot.incidents shouldBe 0
                    }
                }
            }

            `when`("there are monitors of different types") {

                val now = getCurrentTimestamp()
                val downHttpMonitor = createHttpMonitor(httpMonitorRepository, sslCheckEnabled = false)
                val upHttpMonitor = createHttpMonitor(httpMonitorRepository, sslCheckEnabled = false)
                val pushMonitor = createPushMonitor(pushMonitorRepository)
                createDnsMonitor(dnsMonitorRepository, enabled = false)
                // 1 day UP + 1 day DOWN in the period
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downHttpMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusDays(3),
                    endedAt = now.minusDays(1),
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downHttpMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(1),
                    endedAt = null,
                )
                // 2 days UP in the period
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = upHttpMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusDays(3),
                    endedAt = null,
                )
                // 1 day DOWN + 1 day UP in the period
                val pushIncidentEndedAt = now.minusDays(1)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(3),
                    endedAt = pushIncidentEndedAt,
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = pushIncidentEndedAt,
                    endedAt = null,
                )

                val stats = dashboardStats(Duration.ofDays(2))

                then("it should merge the actual stats of every type") {
                    with(stats.actual) {
                        total shouldBe 4
                        up shouldBe 2
                        down shouldBe 1
                        paused shouldBe 1
                        inProgress shouldBe 0
                        inMaintenance shouldBe 0
                    }
                    stats.outsideMaintenance shouldBe MonitorStateCounts(up = 2, down = 1, pending = 0)
                    // The ongoing HTTP incident started later than the push one
                    stats.actual.lastIncident shouldBe
                        stats.byType.first { it.type == MonitorType.HTTP_SSL }.actual.lastIncident
                }

                then("it should merge the historical stats of every type") {
                    // 4 days UP + 2 days DOWN
                    stats.history.uptimeRatio shouldEqualRounded 4.toDouble() / 6
                    val expectedDowntimeSeconds = 2 * 24 * 60 * 60L
                    stats.history.totalDowntimeSeconds shouldBeInRange
                        expectedDowntimeSeconds - 1..expectedDowntimeSeconds + 1
                    stats.history.incidents shouldBe 2
                    // One monitor per type, even if their IDs happen to be the same
                    stats.history.affectedMonitors shouldBe 2
                }

                then("it should tell the ongoing incidents from the resolved ones") {
                    stats.incidents.ongoingOutsideMaintenance shouldBe 1
                    stats.incidents.resolved shouldBe 1
                    // The resolved one lasted 2 days, even if it started before the period
                    stats.incidents.meanTimeToResolveSeconds shouldBe Duration.ofDays(2).seconds
                }

                then("it should break the stats down to the types that have monitors") {
                    stats.byType.map { it.type } shouldContainExactlyInAnyOrder
                        listOf(MonitorType.HTTP_SSL, MonitorType.PUSH, MonitorType.DNS)
                    with(stats.byType.first { it.type == MonitorType.PUSH }) {
                        actual.total shouldBe 1
                        history.uptimeRatio shouldEqualRounded 0.5
                    }
                }

                then("every type should have a timeline of its own, adding up to the merged one") {
                    stats.byType.forAll { it.timeline shouldHaveSize stats.timeline.size }
                    // The push monitor was down for the whole first half of the period
                    stats.byType.first { it.type == MonitorType.PUSH }.timeline
                        .filterNot { it.end.isAfter(pushIncidentEndedAt) }
                        .let { slots ->
                            slots.forAll { it.uptimeSeconds shouldBe 0 }
                            slots.sumOf { it.downtimeSeconds } shouldBeGreaterThan 0
                        }
                    stats.byType.first { it.type == MonitorType.DNS }.timeline.forAll { slot ->
                        slot.uptimeSeconds shouldBe 0
                        slot.downtimeSeconds shouldBe 0
                    }
                    // The push incident started before the period, so it's counted in the first slot
                    stats.byType.first { it.type == MonitorType.PUSH }.timeline.map { it.incidents }.let { incidents ->
                        incidents.first() shouldBe 1
                        incidents.drop(1).forAll { it shouldBe 0 }
                    }
                    stats.timeline.sumOf { it.incidents } shouldBe stats.history.incidents
                    stats.timeline.forEachIndexed { index, slot ->
                        val typeSlots = stats.byType.map { it.timeline[index] }
                        typeSlots.forAll { typeSlot ->
                            typeSlot.start shouldBe slot.start
                            typeSlot.end shouldBe slot.end
                        }
                        slot.uptimeSeconds shouldBe typeSlots.sumOf { it.uptimeSeconds }
                        slot.downtimeSeconds shouldBe typeSlots.sumOf { it.downtimeSeconds }
                        slot.incidents shouldBe typeSlots.sumOf { it.incidents }
                    }
                }
            }

            `when`("there were incidents in different slots of the timeline") {

                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusDays(2),
                    endedAt = now.minusMinutes(150),
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(150),
                    endedAt = now.minusMinutes(90),
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusMinutes(90),
                    endedAt = null,
                )

                val timeline = dashboardStats(Duration.ofDays(1)).timeline

                then("it should slice a day into slots aligned to whole hours, from the start of the period") {
                    // The first and the current slot are partial, unless the period starts on a whole hour
                    timeline.size shouldBeGreaterThanOrEqual 24
                    Duration.between(timeline.first().start, timeline.last().end) shouldBe Duration.ofDays(1)
                    Duration.between(timeline.last().end, getCurrentTimestamp()).seconds shouldBeInRange 0L..1L
                    timeline.zipWithNext().forAll { (slot, nextSlot) -> nextSlot.start shouldBe slot.end }
                    timeline.drop(1).forAll { slot ->
                        with(slot.start.atZoneSameInstant(ZoneId.systemDefault())) {
                            minute shouldBe 0
                            second shouldBe 0
                            nano shouldBe 0
                        }
                    }
                }

                then("it should split the downtime between the slots the incident overlapped") {
                    val incidentStart = now.minusMinutes(150)
                    val incidentEnd = now.minusMinutes(90)
                    timeline.forAll { slot ->
                        val overlapSeconds = Duration.between(
                            maxOf(slot.start, incidentStart),
                            minOf(slot.end, incidentEnd),
                        ).seconds.coerceAtLeast(0)
                        slot.downtimeSeconds shouldBeInRange overlapSeconds - 2..overlapSeconds + 2
                    }
                    timeline.sumOf { it.downtimeSeconds } shouldBeInRange 3600L - 2..3600L + 2
                }

                then("it should count the incident only in the slot it started in") {
                    val incidentStart = now.minusMinutes(150)
                    with(timeline.single { it.incidents > 0 }) {
                        incidents shouldBe 1
                        incidentStart.isBefore(start) shouldBe false
                        incidentStart.isBefore(end) shouldBe true
                    }
                }
            }

            `when`("a monitor is paused now, although it was checked during the period") {

                val now = getCurrentTimestamp()
                val monitor = createPushMonitor(pushMonitorRepository, enabled = false)
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(20),
                    endedAt = now.minusHours(18),
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(18),
                    endedAt = null,
                    updatedAt = now.minusHours(5),
                )

                val stats = dashboardStats(Duration.ofDays(1))

                then("its events should be left out of every figure, just like its incidents are from the lists") {
                    with(stats.history) {
                        incidents shouldBe 0
                        affectedMonitors shouldBe 0
                        uptimeRatio shouldBe null
                        totalDowntimeSeconds shouldBe 0
                    }
                    stats.incidents shouldBe
                        DashboardIncidentStats(
                            ongoingOutsideMaintenance = 0,
                            ongoingInMaintenance = 0,
                            resolved = 0,
                            meanTimeToResolveSeconds = null,
                        )
                    stats.timeline.forAll { slot ->
                        slot.uptimeSeconds shouldBe 0
                        slot.downtimeSeconds shouldBe 0
                        slot.incidents shouldBe 0
                    }
                    stats.leastReliableMonitors.shouldBeEmpty()
                }

                then("it should still be counted as a paused one") {
                    stats.actual.paused shouldBe 1
                    with(stats.byType.single()) {
                        actual.paused shouldBe 1
                        history.incidents shouldBe 0
                    }
                }
            }

            `when`("an event started after the instant the figures are calculated for") {

                val now = getCurrentTimestamp()
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(2),
                    endedAt = now.plusHours(1),
                )
                // E.g. one that started while the events of the other types were fetched
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.plusHours(1),
                    endedAt = null,
                    updatedAt = now.plusHours(1),
                )

                val stats = dashboardStats(Duration.ofDays(1))

                then("it should be left out, instead of adding a negative downtime") {
                    stats.history.incidents shouldBe 0
                    stats.history.totalDowntimeSeconds shouldBe 0
                    stats.timeline.forAll { it.incidents shouldBe 0 }
                    stats.leastReliableMonitors.shouldBeEmpty()
                }
            }

            `when`("an incident has just started") {

                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(2),
                    endedAt = now,
                )
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now,
                    endedAt = null,
                    updatedAt = now,
                )

                val stats = dashboardStats(Duration.ofHours(1))

                then("it should be counted in the last slot already, even without a whole second of downtime") {
                    stats.timeline.last().incidents shouldBe 1
                    stats.timeline.dropLast(1).forAll { it.incidents shouldBe 0 }
                    stats.incidents.ongoingOutsideMaintenance shouldBe 1
                }
            }

            listOf(
                Duration.ofHours(1) to 24,
                Duration.ofDays(7) to 28,
                Duration.ofDays(30) to 30,
                Duration.ofDays(90) to 60,
                // A slot is never shorter than a second
                Duration.ofMillis(10) to 1,
            ).forEach { (period, expectedSlots) ->
                `when`("the period is $period") {

                    val timeline = dashboardStats(period).timeline

                    then("the timeline should consist of $expectedSlots slots covering the whole period") {
                        // One more, if the period doesn't start on a boundary, as the first slot is a partial one then
                        timeline.size shouldBeInRange expectedSlots..expectedSlots + 1
                        Duration.between(timeline.first().start, timeline.last().end) shouldBe period
                    }
                }
            }

            `when`("there are certificates with issues") {

                val now = getCurrentTimestamp()
                fun createMonitorWithSsl(
                    status: SslStatus,
                    validUntil: OffsetDateTime,
                    enabled: Boolean = true,
                    sslCheckEnabled: Boolean = true,
                ) = createHttpMonitor(httpMonitorRepository, enabled = enabled, sslCheckEnabled = sslCheckEnabled)
                    .also { monitor ->
                        createSSLEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = status,
                            startedAt = now.minusDays(1),
                            endedAt = null,
                            sslExpiryDate = validUntil,
                        )
                    }

                val expiresLater = createMonitorWithSsl(SslStatus.WILL_EXPIRE, now.plusDays(20))
                val expiresSooner = createMonitorWithSsl(SslStatus.WILL_EXPIRE, now.plusDays(5))
                val invalid = createMonitorWithSsl(SslStatus.INVALID, now.plusDays(90))
                createMonitorWithSsl(SslStatus.VALID, now.plusDays(90))
                createMonitorWithSsl(SslStatus.WILL_EXPIRE, now.plusDays(3), enabled = false)
                createMonitorWithSsl(SslStatus.INVALID, now.plusDays(3), sslCheckEnabled = false)

                val stats = dashboardStats(Duration.ofDays(7))

                then("it should list the checked ones, the invalid ones first, then the soonest expiring ones") {
                    stats.certificatesWithIssues.map { it.id } shouldBe
                        listOf(invalid.id, expiresSooner.id, expiresLater.id)
                    stats.sslStats.invalid shouldBe 1
                    stats.sslStats.willExpire shouldBe 2
                    stats.sslStats.valid shouldBe 1
                }
            }

            `when`("more certificates have issues than the dashboard lists") {

                val now = getCurrentTimestamp()
                val monitors = List(StatCalculator.CERTIFICATES_WITH_ISSUES_LIMIT + 2) { index ->
                    createHttpMonitor(httpMonitorRepository, monitorName = "Monitor $index").also { monitor ->
                        createSSLEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = SslStatus.WILL_EXPIRE,
                            startedAt = now.minusDays(1),
                            endedAt = null,
                            sslExpiryDate = now.plusDays(index + 1L),
                        )
                    }
                }

                val stats = dashboardStats(Duration.ofDays(7))

                then("only the soonest expiring ones should be listed, but every one of them should be counted") {
                    stats.certificatesWithIssues.map { it.id } shouldBe
                        monitors.take(StatCalculator.CERTIFICATES_WITH_ISSUES_LIMIT).map { it.id }
                    stats.sslStats.willExpire shouldBe monitors.size
                }
            }

            `when`("some of the monitors went down during the period") {

                val now = getCurrentTimestamp()
                val pushDown = createPushMonitor(pushMonitorRepository, monitorName = "Push down")
                val dnsResolved = createDnsMonitor(dnsMonitorRepository, monitorName = "DNS resolved")
                val dnsPaused = createDnsMonitor(dnsMonitorRepository, enabled = false, monitorName = "DNS paused")
                val httpFlaky = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP flaky")
                val tcpOnce = createTcpMonitor(tcpMonitorRepository, monitorName = "TCP once")
                val icmpTieA = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP tie A")
                val icmpTieB = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP tie B")
                val httpHealthy = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP healthy")
                // 3 hours, still ongoing
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushDown.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = null,
                    updatedAt = now,
                )
                // 2 hours
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = dnsResolved.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(5),
                    endedAt = now.minusHours(3),
                )
                // 10 hours, but it's paused now
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = dnsPaused.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(15),
                    endedAt = now.minusHours(5),
                )
                // 2 x 20 minutes
                listOf(now.minusHours(3), now.minusHours(2)).forEach { startedAt ->
                    createHttpUptimeEventRecord(
                        dslContext = dslContext,
                        monitorId = httpFlaky.id,
                        status = UptimeStatus.DOWN,
                        startedAt = startedAt,
                        endedAt = startedAt.plusMinutes(20),
                    )
                }
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = httpFlaky.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusMinutes(100),
                    endedAt = null,
                )
                // 40 minutes at once
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = tcpOnce.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusMinutes(80),
                )
                createMaintenanceWindow(
                    dslContext = dslContext,
                    monitors = listOf(MonitorID(MonitorType.TCP, tcpOnce.name)),
                )
                // 10 minutes each
                listOf(icmpTieB, icmpTieA).forEach { monitor ->
                    createIcmpUptimeEventRecord(
                        dslContext = dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusHours(1),
                        endedAt = now.minusMinutes(50),
                    )
                }
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = httpHealthy.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusDays(2),
                    endedAt = null,
                )

                val leastReliableMonitors =
                    dashboardStats(Duration.ofDays(1)).leastReliableMonitors

                then("they should be ranked by their downtime, their incidents, then their names, up to the limit") {
                    leastReliableMonitors shouldHaveSize StatCalculator.LEAST_RELIABLE_MONITORS_LIMIT
                    // The paused one is left out, no matter how long it was down
                    leastReliableMonitors.map { it.name } shouldBe
                        listOf("Push down", "DNS resolved", "HTTP flaky", "TCP once", "ICMP tie A")
                }

                then("every one of them should be identified by its type and ID") {
                    leastReliableMonitors.map { it.id } shouldBe listOf(
                        NumericMonitorID(MonitorType.PUSH, pushDown.id),
                        NumericMonitorID(MonitorType.DNS, dnsResolved.id),
                        NumericMonitorID(MonitorType.HTTP_SSL, httpFlaky.id),
                        NumericMonitorID(MonitorType.TCP, tcpOnce.id),
                        NumericMonitorID(MonitorType.ICMP, icmpTieA.id),
                    )
                }

                then("every one of them should tell its current state") {
                    leastReliableMonitors.map { it.uptimeStatus to it.inMaintenance } shouldBe listOf(
                        UptimeStatus.DOWN to false,
                        null to false,
                        UptimeStatus.UP to false,
                        null to true,
                        null to false,
                    )
                }

                then("every one of them should have its own figures of the period") {
                    with(leastReliableMonitors.first { it.name == "HTTP flaky" }.history) {
                        incidents shouldBe 2
                        totalDowntimeSeconds shouldBeInRange 40 * 60L - 1..40 * 60L + 1
                        uptimeRatio.shouldNotBeNull()
                    }
                }
            }

            `when`("there are monitors under an active maintenance window") {

                val now = getCurrentTimestamp()
                val maintainedPendingMonitor = createTcpMonitor(tcpMonitorRepository)
                val maintainedUpMonitor = createTcpMonitor(tcpMonitorRepository)
                val maintainedDownMonitor = createTcpMonitor(tcpMonitorRepository)
                val downMonitor = createTcpMonitor(tcpMonitorRepository)
                listOf(
                    maintainedUpMonitor to UptimeStatus.UP,
                    maintainedDownMonitor to UptimeStatus.DOWN,
                    downMonitor to UptimeStatus.DOWN,
                ).forEach { (monitor, status) ->
                    createTcpUptimeEventRecord(
                        dslContext = dslContext,
                        monitorId = monitor.id,
                        status = status,
                        startedAt = now.minusHours(1),
                        endedAt = null,
                    )
                }
                createMaintenanceWindow(
                    dslContext = dslContext,
                    monitors = listOf(maintainedPendingMonitor, maintainedUpMonitor, maintainedDownMonitor)
                        .map { MonitorID(MonitorType.TCP, it.name) },
                )

                val stats = dashboardStats(Duration.ofDays(7))

                then("they should be identified by their type and ID") {
                    stats.actual.inMaintenance shouldBe 3
                    stats.monitorsInMaintenance shouldBe
                        listOf(maintainedPendingMonitor, maintainedUpMonitor, maintainedDownMonitor)
                            .map { NumericMonitorID(MonitorType.TCP, it.id) }
                            .toSet()
                }

                then("their states should be counted separately") {
                    stats.actual.down shouldBe 2
                    stats.inMaintenance shouldBe MonitorStateCounts(up = 1, down = 1, pending = 1)
                    stats.byType.single().inMaintenance shouldBe MonitorStateCounts(up = 1, down = 1, pending = 1)
                    stats.outsideMaintenance shouldBe MonitorStateCounts(up = 0, down = 1, pending = 0)
                    stats.byType.single().outsideMaintenance shouldBe MonitorStateCounts(up = 0, down = 1, pending = 0)
                    stats.incidents.ongoingOutsideMaintenance shouldBe 1
                    stats.incidents.ongoingInMaintenance shouldBe 1
                }
            }

            `when`("there is an active global maintenance window") {

                val enabledMonitor = createTcpMonitor(tcpMonitorRepository)
                val otherEnabledMonitor = createDnsMonitor(dnsMonitorRepository)
                createTcpMonitor(tcpMonitorRepository, enabled = false)
                createMaintenanceWindow(dslContext, global = true)

                val stats = dashboardStats(Duration.ofDays(7))

                then("every enabled monitor should be under maintenance, of every type") {
                    stats.monitorsInMaintenance shouldBe setOf(
                        NumericMonitorID(MonitorType.TCP, enabledMonitor.id),
                        NumericMonitorID(MonitorType.DNS, otherEnabledMonitor.id),
                    )
                }
            }

            `when`("there is an active maintenance window of a category") {

                val httpInCategory = createHttpMonitor(httpMonitorRepository, category = "Database")
                val dnsInCategory = createDnsMonitor(dnsMonitorRepository, category = "Database")
                createHttpMonitor(httpMonitorRepository, category = "Web")
                createDnsMonitor(dnsMonitorRepository)
                createMaintenanceWindow(dslContext, categories = listOf("Database"))

                val stats = dashboardStats(Duration.ofDays(7))

                then("only the monitors of that category should be under maintenance, of every type") {
                    stats.monitorsInMaintenance shouldBe setOf(
                        NumericMonitorID(MonitorType.HTTP_SSL, httpInCategory.id),
                        NumericMonitorID(MonitorType.DNS, dnsInCategory.id),
                    )
                }
            }

            `when`("the maintenance windows of a monitor are either disabled or not active") {

                val monitor = createTcpMonitor(tcpMonitorRepository)
                val monitorId = MonitorID(MonitorType.TCP, monitor.name)
                createMaintenanceWindow(dslContext, enabled = false, monitors = listOf(monitorId))
                createMaintenanceWindow(
                    dslContext,
                    start = getCurrentTimestamp().plusDays(1),
                    duration = "PT1H",
                    monitors = listOf(monitorId),
                )

                val stats = dashboardStats(Duration.ofDays(7))

                then("it shouldn't be under maintenance") {
                    stats.monitorsInMaintenance.shouldBeEmpty()
                    stats.actual.inMaintenance shouldBe 0
                }
            }

            `when`("an ongoing incident was last updated before a later incident that ended before the period") {

                val now = getCurrentTimestamp()
                val staleMonitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = staleMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(1),
                    endedAt = null,
                    updatedAt = now.minusHours(20),
                )
                val otherMonitor = createTcpMonitor(tcpMonitorRepository)
                val laterIncident = createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = otherMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusHours(2),
                )

                val stats = dashboardStats(Duration.ofHours(1))

                then("the latest one should be found, even if it's not among the events of the period") {
                    stats.actual.lastIncident shouldBe laterIncident.updatedAt
                }
            }

            `when`("the latest incident happened before the period") {

                val now = getCurrentTimestamp()
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val incident = createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(3),
                    endedAt = now.minusDays(2),
                )

                val stats = dashboardStats(Duration.ofDays(1))

                then("it should still be found in the whole history") {
                    stats.actual.lastIncident shouldBe incident.updatedAt
                }
            }

            `when`("the latest incident happened in the period") {

                val now = getCurrentTimestamp()
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(3),
                    endedAt = now.minusDays(2),
                )
                val latestIncident = createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusHours(2),
                )

                val stats = dashboardStats(Duration.ofDays(1))

                then("it should be the latest one") {
                    stats.actual.lastIncident shouldBe latestIncident.updatedAt
                }
            }
        }

        given("the fetchSummaries() method of the monitor repositories") {

            `when`("there are monitors of every type") {

                val now = getCurrentTimestamp()
                val http = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP", category = "Web")
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = http.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext = dslContext,
                    monitorId = http.id,
                    status = SslStatus.INVALID,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                    sslExpiryDate = now.plusDays(3),
                    error = "Expired",
                )
                val push = createPushMonitor(pushMonitorRepository, monitorName = "Push", enabled = false)
                val icmp = createIcmpMonitor(icmpMonitorRepository, monitorName = "ICMP")
                createIcmpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = icmp.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )
                val tcp = createTcpMonitor(tcpMonitorRepository, monitorName = "TCP")
                createTcpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = tcp.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )
                val dns = createDnsMonitor(dnsMonitorRepository, monitorName = "DNS", category = "Resolvers")
                createDnsUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = dns.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )
                val docker = createDockerMonitor(dockerMonitorRepository, monitorName = "Docker")
                // A closed one doesn't tell the current state
                createDockerUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = docker.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )

                val summaries = monitorRepositories.flatMap { it.fetchSummaries() }

                then("they should hold the properties the statistics rely on") {
                    val httpSummary = summaries.filterIsInstance<HttpMonitorSummary>().single()
                    httpSummary.sslValidUntil.shouldNotBeNull()
                    // The expiry date is only checked to be there, as its precision is truncated by the database
                    val comparableSummaries = summaries.map {
                        if (it is HttpMonitorSummary) it.copy(sslValidUntil = null) else it
                    }
                    comparableSummaries shouldContainExactlyInAnyOrder
                        listOf(
                            HttpMonitorSummary(
                                id = http.id,
                                name = "HTTP",
                                enabled = true,
                                category = "Web",
                                uptimeStatus = UptimeStatus.DOWN,
                                sslCheckEnabled = true,
                                sslStatus = SslStatus.INVALID,
                                sslValidUntil = null,
                                sslError = "Expired",
                            ),
                            PushMonitorSummary(push.id, "Push", enabled = false, null, null),
                            IcmpMonitorSummary(icmp.id, "ICMP", enabled = true, null, UptimeStatus.UP),
                            TcpMonitorSummary(tcp.id, "TCP", enabled = true, null, UptimeStatus.DOWN),
                            DnsMonitorSummary(dns.id, "DNS", enabled = true, "Resolvers", UptimeStatus.UP),
                            DockerMonitorSummary(docker.id, "Docker", enabled = true, null, null),
                        )
                    // Every summary tells its own type
                    summaries.map { it.type } shouldContainExactlyInAnyOrder MonitorType.entries
                    summaries.single { it.type == MonitorType.TCP }.monitorId shouldBe MonitorID(MonitorType.TCP, "TCP")
                }
            }
        }
    }
}
