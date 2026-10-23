package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val PERIOD = Duration.ofHours(1)

/**
 * Covers the queries of the ICMP metrics log that aren't shared with the other types, see
 * [DockerMetricsLogRepositoryTest] for the shared ones.
 */
@MicronautTest(startApplication = false)
class IcmpMetricsLogRepositoryTest(
    private val metricsLogRepository: IcmpMetricsLogRepository,
    private val monitorRepository: IcmpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the getAveragePacketLosses() method") {

            `when`("there are logs of several monitors, some of them older than the period") {
                val monitor = createIcmpMonitor(monitorRepository)
                val otherMonitor = createIcmpMonitor(monitorRepository)
                val monitorWithOldLogsOnly = createIcmpMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), packetLoss = 0)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), packetLoss = 50)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), packetLoss = 100)
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(5), packetLoss = 100)
                insertLogAt(monitorWithOldLogsOnly.id, createdAt = minutesAgo(120), packetLoss = 100)

                val result = metricsLogRepository.getAveragePacketLosses(
                    monitorIds = listOf(monitor.id, otherMonitor.id, monitorWithOldLogsOnly.id),
                    period = PERIOD,
                )

                then("it should average the packet losses of every given monitor within the period") {
                    result shouldBe mapOf(monitor.id to 25, otherMonitor.id to 100)
                }
            }

            `when`("no monitor is given") {

                then("it should return an empty map") {
                    metricsLogRepository.getAveragePacketLosses(emptyList(), PERIOD) shouldBe emptyMap()
                }
            }
        }

        given("the fetchIcmpSeries() method") {

            `when`("there are checks with and without a latency") {
                val monitor = createIcmpMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(90), packetLoss = 50, latencyMs = 1)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), packetLoss = 100, latencyMs = null)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), packetLoss = 0, latencyMs = 15)

                val series = metricsLogRepository
                    .fetchIcmpSeries(monitor.id, minutesAgo(60), null, Duration.ofSeconds(1))

                then("both the latency and the packet loss of every check of the period are returned, oldest first") {
                    series.latencies shouldBe listOf(null, 15)
                    series.packetLossPercentages shouldBe listOf(100, 0)
                    series.timestamps shouldBe
                        listOf(minutesAgo(20), minutesAgo(10)).map { it.toInstant().toEpochMilli() }
                }
            }
        }

        given("the fetchIcmpSeries() method with buckets") {

            `when`("most of the checks of a bucket failed") {
                val monitor = createIcmpMonitor(monitorRepository)
                val bucket = now.truncatedTo(ChronoUnit.HOURS).minusHours(3)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(10), packetLoss = 0, latencyMs = 20)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(20), packetLoss = 100, latencyMs = null)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(30), packetLoss = 100, latencyMs = null)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(40), packetLoss = 50, latencyMs = 40)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(70), packetLoss = 100, latencyMs = null)

                val series = metricsLogRepository.fetchIcmpSeries(monitor.id, bucket, null, Duration.ofHours(1))

                then("the readings of the checks are summarized, the failed ones left out of the latency") {
                    series.latencies.first() shouldBe 30
                    series.lowestLatencies.first() shouldBe 20
                    series.highestLatencies.first() shouldBe 40
                    series.packetLossPercentages.first() shouldBe 63
                    series.lowestPacketLossPercentages.first() shouldBe 0
                    series.highestPacketLossPercentages.first() shouldBe 100
                }

                then("a bucket of failed checks only has no latency, but still has its packet loss") {
                    series.latencies.last().shouldBeNull()
                    series.lowestLatencies.last().shouldBeNull()
                    series.highestLatencies.last().shouldBeNull()
                    series.packetLossPercentages.last() shouldBe 100
                }
            }
        }

        given("the getPacketLossMetricsBetween() method") {

            `when`("there are checks before, during and after the period") {
                val monitor = createIcmpMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(90), packetLoss = 100)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), packetLoss = 20)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), packetLoss = 40)
                insertLogAt(monitor.id, createdAt = minutesAgo(1), packetLoss = 100)

                then("only the checks of the period are aggregated") {
                    val result = metricsLogRepository
                        .getPacketLossMetricsBetween(monitor.id, minutesAgo(60), minutesAgo(10))
                        .shouldNotBeNull()

                    result.avg shouldBe 30
                    result.max shouldBe 40
                }
            }
        }

        given("the inherited getAverageLatencies() method") {

            `when`("a monitor has logs without a latency, because none of its packets came back") {
                val monitor = createIcmpMonitor(monitorRepository)
                val unreachableMonitor = createIcmpMonitor(monitorRepository)
                metricsLogRepository.insertLog(monitor.id, latencyMs = 20, packetLossPercentage = 0)
                metricsLogRepository.insertLog(monitor.id, latencyMs = null, packetLossPercentage = 100)
                metricsLogRepository.insertLog(unreachableMonitor.id, latencyMs = null, packetLossPercentage = 100)

                then("it should leave those logs out, and the monitors without any latency too") {
                    metricsLogRepository.getAverageLatencies(listOf(monitor.id, unreachableMonitor.id), PERIOD) shouldBe
                        mapOf(monitor.id to 20)
                }
            }
        }
    }

    // A single instant in whole seconds, so the timestamps can be compared with the epoch milliseconds of the series
    private val now = getCurrentTimestamp().truncatedTo(ChronoUnit.SECONDS)

    private fun minutesAgo(minutes: Long): OffsetDateTime = now.minusMinutes(minutes)

    private fun insertLogAt(monitorId: Long, createdAt: OffsetDateTime, packetLoss: Int, latencyMs: Int? = 10) {
        dslContext.insertInto(ICMP_METRICS_LOG)
            .set(
                IcmpMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setPacketLossPercentage(packetLoss)
                    .setCreatedAt(createdAt)
            )
            .execute()
    }
}
