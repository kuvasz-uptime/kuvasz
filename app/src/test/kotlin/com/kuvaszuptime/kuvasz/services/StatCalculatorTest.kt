package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext
import java.time.Duration

@MicronautTest(startApplication = false)
class StatCalculatorTest(
    monitorRepository: MonitorRepository,
    dslContext: DSLContext,
    statCalculator: StatCalculator,
) : DatabaseBehaviorSpec({

    given("the calculateOverallStats method") {

        `when`("there is a paused monitor") {

            val enabledUpMonitor = createMonitor(monitorRepository, enabled = true)
            val enabledDownMonitor = createMonitor(monitorRepository, enabled = true)
            val pausedMonitor = createMonitor(monitorRepository, enabled = false)

            // enabledUpMonitor's incidents
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = enabledUpMonitor.id,
                startedAt = getCurrentTimestamp().minusDays(10),
                status = UptimeStatus.DOWN,
                endedAt = getCurrentTimestamp().minusDays(5),
            )
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = enabledUpMonitor.id,
                startedAt = getCurrentTimestamp().minusDays(5),
                status = UptimeStatus.UP,
                endedAt = null,
            )
            createSSLEventRecord(
                dslContext = dslContext,
                monitorId = enabledUpMonitor.id,
                status = SslStatus.INVALID,
                startedAt = getCurrentTimestamp().minusDays(10),
                endedAt = null,
            )
            // enabledDownMonitor's incidents
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = enabledDownMonitor.id,
                startedAt = getCurrentTimestamp().minusHours(3),
                status = UptimeStatus.DOWN,
                endedAt = null,
            )
            createSSLEventRecord(
                dslContext = dslContext,
                monitorId = enabledDownMonitor.id,
                status = SslStatus.VALID,
                startedAt = getCurrentTimestamp().minusDays(10),
                endedAt = null,
            )
            // pausedMonitor's incidents
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = pausedMonitor.id,
                startedAt = getCurrentTimestamp().minusDays(2),
                status = UptimeStatus.DOWN,
                endedAt = null,
            )
            createSSLEventRecord(
                dslContext = dslContext,
                monitorId = pausedMonitor.id,
                status = SslStatus.VALID,
                startedAt = getCurrentTimestamp().minusDays(2),
                endedAt = null,
            )

            then("it should ignore them in the statistics") {
                val stats = statCalculator.calculateOverallStats(Duration.ofDays(6))
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
            }
        }

        `when`("there is a monitor that was just created") {

            createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)
            val oldMonitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)

            // Old monitor's events
            createUptimeEventRecord(
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

                val stats = statCalculator.calculateOverallStats(Duration.ofDays(6))
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

            val monitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)

            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor.id,
                startedAt = getCurrentTimestamp().minusDays(12),
                status = UptimeStatus.DOWN,
                endedAt = getCurrentTimestamp().minusDays(10),
            )
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor.id,
                startedAt = getCurrentTimestamp().minusDays(10),
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

            val stats = statCalculator.calculateOverallStats(Duration.ofDays(9))

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

                stats.history.uptimeStats.incidents shouldBe 0
                stats.history.uptimeStats.affectedMonitors shouldBe 0
                stats.history.uptimeStats.uptimeRatio shouldBe 1.0 // Only UP event in the period
            }
        }

        `when`("monitors with all the exposed statuses are present") {

            val upMonitorInProgress = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)
            val upMonitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = false)
            val downMonitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)
            val pausedMonitor = createMonitor(monitorRepository, enabled = false, sslCheckEnabled = true)
            val validSSLMonitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)
            createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true) // sslInProgressMonitor

            // upMonitorInProgress's events: in progress UPTIME check + INVALID SSL
            createSSLEventRecord(
                dslContext = dslContext,
                monitorId = upMonitorInProgress.id,
                status = SslStatus.INVALID,
                startedAt = getCurrentTimestamp().minusDays(10),
                endedAt = null,
            )

            // upMonitor's events: UP + waiting for SSL check (should not be counted, because of disabled SSL check)
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = upMonitor.id,
                startedAt = getCurrentTimestamp().minusDays(10),
                status = UptimeStatus.UP,
                endedAt = null,
            )

            // downMonitor's events: DOWN + WILL_EXPIRE SSL
            createUptimeEventRecord(
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
            createUptimeEventRecord(
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
            createUptimeEventRecord(
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
                val stats = statCalculator.calculateOverallStats(Duration.ofDays(6))

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
            }
        }

        `when`("there are no events in the given period") {

            val monitor = createMonitor(monitorRepository, enabled = true, sslCheckEnabled = true)
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor.id,
                startedAt = getCurrentTimestamp().minusDays(10),
                status = UptimeStatus.UP,
                endedAt = getCurrentTimestamp().minusDays(6).minusSeconds(1),
            )

            val stats = statCalculator.calculateOverallStats(Duration.ofDays(6))

            then("it should handle it gracefully and return null as the ratio") {

                stats.history.uptimeStats.uptimeRatio shouldBe null
            }
        }

        `when`("there are no monitors at all") {

            then("it should return empty stats") {
                val stats = statCalculator.calculateOverallStats(Duration.ofDays(6))

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
            }
        }

        `when`("there are multiple events for a given period") {

            val monitor1 = createMonitor(monitorRepository)
            val monitor2 = createMonitor(monitorRepository)

            val firstUpStartedAt = getCurrentTimestamp().minusDays(10)
            val firstUpEndedAt = getCurrentTimestamp().minusDays(5)

            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor1.id,
                status = UptimeStatus.UP,
                startedAt = firstUpStartedAt,
                endedAt = firstUpEndedAt,
            )
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor1.id,
                startedAt = firstUpEndedAt,
                status = UptimeStatus.DOWN,
                endedAt = null,
            )

            val secondDownStartedAt = getCurrentTimestamp().minusDays(3)
            val secondDownEndedAt = getCurrentTimestamp().minusDays(1)
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor2.id,
                status = UptimeStatus.DOWN,
                startedAt = secondDownStartedAt,
                endedAt = secondDownEndedAt,
            )
            createUptimeEventRecord(
                dslContext = dslContext,
                monitorId = monitor2.id,
                status = UptimeStatus.UP,
                startedAt = secondDownEndedAt,
                endedAt = null,
            )

            val stats = statCalculator.calculateOverallStats(Duration.ofDays(12))

            then("it should calculate the uptimeRatio correctly & return the last incident timestamp") {

                // 5 days UP + 5 days DOWN for monitor1, 1 day UP + 2 days DOWN for monitor2
                stats.history.uptimeStats.uptimeRatio shouldBe 6.toDouble() / 13
                stats.actual.uptimeStats.lastIncident shouldBe secondDownEndedAt
            }
        }
    }
})
