package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
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

        given("the fetchIcmpChartPoints() method") {

            `when`("it's called with a resolution") {
                val monitor = createIcmpMonitor(monitorRepository)
                val quarter = getCurrentTimestamp().truncatedTo(ChronoUnit.HOURS).minusHours(1)
                insertLogAt(monitor.id, createdAt = quarter.plusMinutes(1), packetLoss = 0, latencyMs = 20)
                insertLogAt(monitor.id, createdAt = quarter.plusMinutes(2), packetLoss = 100, latencyMs = null)
                insertLogAt(monitor.id, createdAt = quarter.plusMinutes(3), packetLoss = 50, latencyMs = 40)

                val points = metricsLogRepository.fetchIcmpChartPoints(
                    monitor.id,
                    Duration.ofDays(7),
                    Duration.ofMinutes(15),
                )

                then("both the latency and the packet loss get their average and their highest reading") {
                    val point = points.single()
                    point.createdAt.toInstant() shouldBe quarter.toInstant()
                    point.latencyInMs shouldBe 30
                    point.maxLatencyInMs shouldBe 40
                    point.packetLossPercentage shouldBe 50
                    point.maxPacketLossPercentage shouldBe 100
                }
            }

            `when`("it's called without a resolution") {
                val monitor = createIcmpMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), packetLoss = 100, latencyMs = null)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), packetLoss = 0, latencyMs = 15)

                val points = metricsLogRepository.fetchIcmpChartPoints(monitor.id, PERIOD, resolution = null)

                then("every log is a point, the newest first, without the highest readings") {
                    points.map { listOf(it.latencyInMs, it.packetLossPercentage) } shouldBe
                        listOf(listOf(15, 0), listOf(null, 100))
                    points.map { listOf(it.maxLatencyInMs, it.maxPacketLossPercentage) } shouldBe
                        listOf(listOf(null, null), listOf(null, null))
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

    private fun minutesAgo(minutes: Long): OffsetDateTime = getCurrentTimestamp().minusMinutes(minutes)

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
