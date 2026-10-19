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

    private fun insertLogAt(monitorId: Long, createdAt: OffsetDateTime, packetLoss: Int) {
        dslContext.insertInto(ICMP_METRICS_LOG)
            .set(
                IcmpMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(10)
                    .setPacketLossPercentage(packetLoss)
                    .setCreatedAt(createdAt)
            )
            .execute()
    }
}
