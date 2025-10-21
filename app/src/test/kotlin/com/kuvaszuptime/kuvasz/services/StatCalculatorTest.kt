package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.testutils.shouldEqualRounded
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext
import java.time.Duration
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class StatCalculatorTest(
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    dslContext: DSLContext,
    statCalculator: StatCalculator,
) : DatabaseBehaviorSpec({

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
            // Event that overlaps with the start of the 9 days period, downtime should be counted from the beginning
            // of the given period
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
                stats.history.uptimeStats.totalDowntimeSeconds shouldBe 2 * 24 * 60 * 60
            }
        }

        `when`("monitors with all the exposed statuses are present") {

            val upMonitorInProgress = createHttpMonitor(httpMonitorRepository, enabled = true, sslCheckEnabled = true)
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
                stats.history.uptimeStats.totalDowntimeSeconds shouldBe 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60
                stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
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
            // Event that overlaps with the start of the 9 days period, downtime should be counted from the beginning
            // of the given period
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
                stats.history.uptimeStats.totalDowntimeSeconds shouldBe 2 * 24 * 60 * 60
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
                stats.history.uptimeStats.totalDowntimeSeconds shouldBe 5 * 24 * 60 * 60 + 2 * 24 * 60 * 60
                stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
            }
        }
    }

    given("the calculateHistoricalHttpUptimeStats(monitor) method") {

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
                val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalHttpUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = upMonitorInProgress.id,
                )
                statsOfInProgressUpMonitor.incidents shouldBe 0
                statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                val statsOfUpMonitor = statCalculator.calculateHistoricalHttpUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = upMonitor.id,
                )
                statsOfUpMonitor.incidents shouldBe 0
                statsOfUpMonitor.affectedMonitors shouldBe 0
                statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                statsOfUpMonitor.uptimeRatio shouldBe 1.0

                val statsOfDownMonitor = statCalculator.calculateHistoricalHttpUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = downMonitor.id,
                )
                statsOfDownMonitor.incidents shouldBe 1
                statsOfDownMonitor.affectedMonitors shouldBe 1
                val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                    expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                statsOfDownMonitor.uptimeRatio shouldBe 0.0

                val statsOfPausedMonitor = statCalculator.calculateHistoricalHttpUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = pausedMonitor.id,
                )
                statsOfPausedMonitor.incidents shouldBe 1
                statsOfPausedMonitor.affectedMonitors shouldBe 1
                statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                val statsOfPausedMonitor2 = statCalculator.calculateHistoricalHttpUptimeStats(
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

    given("the calculateHistoricalPushUptimeStats(monitor) method") {

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
                val statsOfInProgressUpMonitor = statCalculator.calculateHistoricalPushUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = upMonitorInProgress.id,
                )
                statsOfInProgressUpMonitor.incidents shouldBe 0
                statsOfInProgressUpMonitor.affectedMonitors shouldBe 0
                statsOfInProgressUpMonitor.totalDowntimeSeconds shouldBe 0
                statsOfInProgressUpMonitor.uptimeRatio shouldBe null

                val statsOfUpMonitor = statCalculator.calculateHistoricalPushUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = upMonitor.id,
                )
                statsOfUpMonitor.incidents shouldBe 0
                statsOfUpMonitor.affectedMonitors shouldBe 0
                statsOfUpMonitor.totalDowntimeSeconds shouldBe 0
                statsOfUpMonitor.uptimeRatio shouldBe 1.0

                val statsOfDownMonitor = statCalculator.calculateHistoricalPushUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = downMonitor.id,
                )
                statsOfDownMonitor.incidents shouldBe 1
                statsOfDownMonitor.affectedMonitors shouldBe 1
                val expectedDowntimeSeconds = 5L * 24 * 60 * 60 // 5 days in seconds
                statsOfDownMonitor.totalDowntimeSeconds shouldBeInRange
                    expectedDowntimeSeconds..expectedDowntimeSeconds + 1
                statsOfDownMonitor.uptimeRatio shouldBe 0.0

                val statsOfPausedMonitor = statCalculator.calculateHistoricalPushUptimeStats(
                    period = Duration.ofDays(6),
                    monitorId = pausedMonitor.id,
                )
                statsOfPausedMonitor.incidents shouldBe 1
                statsOfPausedMonitor.affectedMonitors shouldBe 1
                statsOfPausedMonitor.totalDowntimeSeconds shouldBe 24 * 60 * 60 // 1 day
                statsOfPausedMonitor.uptimeRatio shouldBe 0.5

                val statsOfPausedMonitor2 = statCalculator.calculateHistoricalPushUptimeStats(
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

    given("the generateUptimeHistoryOverview() method") {

        fun createTestEvent(
            status: UptimeStatus,
            startedAt: OffsetDateTime,
            endedAt: OffsetDateTime? = null,
            updatedAt: OffsetDateTime,
        ) = UptimeEventCalculationContext(
            monitorId = 1,
            isMonitorEnabled = true,
            status = status,
            startedAt = startedAt,
            endedAt = endedAt,
            updatedAt = updatedAt,
        )

        `when`("there is no event for a given day") {

            val event1 = createTestEvent(
                status = UptimeStatus.UP,
                startedAt = getCurrentTimestamp().minusDays(9),
                endedAt = getCurrentTimestamp().minusDays(8),
                updatedAt = getCurrentTimestamp().minusDays(8),
            )
            val event2 = createTestEvent(
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(8),
                endedAt = getCurrentTimestamp().minusDays(7),
                updatedAt = getCurrentTimestamp().minusDays(7),
            )

            then("it should return null for that day as outageCnt") {

                val result = statCalculator.generateUptimeHistoryOverview(
                    period = Duration.ofDays(11),
                    uptimeEvents = listOf(event1, event2),
                )

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
                // Second effective event is 8 days ago with DOWN status, so the third day should have 1 as outageCnt
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

            val event1 = createTestEvent(
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(10),
                endedAt = getCurrentTimestamp().minusDays(3),
                updatedAt = getCurrentTimestamp().minusDays(3),
            )

            val event2 = createTestEvent(
                status = UptimeStatus.UP,
                startedAt = getCurrentTimestamp().minusDays(3),
                endedAt = null,
                updatedAt = getCurrentTimestamp(),
            )

            then("it should count that event on the days within the period") {

                val result = statCalculator.generateUptimeHistoryOverview(
                    period = Duration.ofDays(7),
                    uptimeEvents = listOf(event1, event2),
                )

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

            val event1 = createTestEvent(
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(10),
                endedAt = getCurrentTimestamp().minusDays(3),
                updatedAt = getCurrentTimestamp().minusDays(3),
            )

            val event2 = createTestEvent(
                status = UptimeStatus.UP,
                startedAt = getCurrentTimestamp().minusDays(3),
                endedAt = null,
                updatedAt = getCurrentTimestamp().minusDays(1),
            )

            then("its updateDate should be the base of the calculation") {

                val result = statCalculator.generateUptimeHistoryOverview(
                    period = Duration.ofDays(7),
                    uptimeEvents = listOf(event1, event2),
                )
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
    }
})
