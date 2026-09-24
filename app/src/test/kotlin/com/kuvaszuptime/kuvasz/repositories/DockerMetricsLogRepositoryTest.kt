package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.math.BigDecimal
import java.time.Duration

private val PERIOD = Duration.ofHours(1)

private fun stats(cpu: Double?, memory: Long?) = DockerContainerStats(
    cgroupVersion = DockerCgroupVersion.V2,
    cpuUsagePercent = cpu,
    memoryUsageBytes = memory,
    memoryLimitBytes = 8L * 1024 * 1024 * 1024,
)

/**
 * Covers the aggregation the shared [MonitorMetricsLogRepository] runs, which every other type's spec stubs out.
 * The Docker log is the one that exercises all three column types it has to cope with: an `Int` latency, a `Long`
 * memory reading that overflows an `Int`, and a fractional CPU percentage that must not be rounded to whole units.
 */
@MicronautTest(startApplication = false)
class DockerMetricsLogRepositoryTest(
    private val metricsLogRepository: DockerMetricsLogRepository,
    private val monitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the getCpuUsageMetrics() method") {

            `when`("there are logs with fractional CPU readings") {
                val monitor = createDockerMonitor(monitorRepository)
                listOf(10.0, 20.0, 30.0, 40.5).forEach {
                    metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(it, 1_000))
                }

                val result = metricsLogRepository.getCpuUsageMetrics(monitor.id, PERIOD)

                then("it should keep the decimals of the average instead of rounding to whole percents") {
                    // 100.5 / 4 = 25.125, trimmed to the scale the column is declared with
                    result?.avg.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("25.13")
                }

                then("it should report the minimum and the maximum with their decimals") {
                    val nonNullResult = result.shouldNotBeNull()
                    nonNullResult.min.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("10.00")
                    nonNullResult.max.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("40.50")
                }
            }

            // Rounding to whole percents used to be the shared behaviour, and it would flatten an idle container to 0
            `when`("every reading is below a whole percent") {
                val monitor = createDockerMonitor(monitorRepository)
                listOf(0.2, 0.4, 0.6).forEach {
                    metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(it, 1_000))
                }

                then("the average should still be non-zero") {
                    val result = metricsLogRepository.getCpuUsageMetrics(monitor.id, PERIOD)

                    result?.avg.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("0.40")
                }
            }

            `when`("some of the logs carry no CPU reading") {
                val monitor = createDockerMonitor(monitorRepository)
                metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(10.0, 1_000))
                metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(null, 1_000))
                metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = null)

                then("the rows without one should be left out of the average") {
                    val result = metricsLogRepository.getCpuUsageMetrics(monitor.id, PERIOD)

                    result?.avg.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("10.00")
                }
            }

            `when`("there is no log at all") {
                val monitor = createDockerMonitor(monitorRepository)

                then("it should return null") {
                    metricsLogRepository.getCpuUsageMetrics(monitor.id, PERIOD).shouldBeNull()
                }
            }

            `when`("every log is older than the period") {
                val monitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, cpu = BigDecimal("10.00"), minutesAgo = 120)

                then("it should return null") {
                    metricsLogRepository.getCpuUsageMetrics(monitor.id, PERIOD).shouldBeNull()
                }
            }
        }

        given("the getMemoryUsageMetrics() method") {

            // A container with a multi-gigabyte footprint overflows an Int, which is what the measurement used to be
            `when`("the readings are larger than an Int can hold") {
                val monitor = createDockerMonitor(monitorRepository)
                val twoGiB = 2L * 1024 * 1024 * 1024
                val sixGiB = 6L * 1024 * 1024 * 1024
                listOf(twoGiB, sixGiB).forEach {
                    metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(1.0, it))
                }

                val result = metricsLogRepository.getMemoryUsageMetrics(monitor.id, PERIOD)

                then("it should report them without overflowing") {
                    result?.min shouldBe twoGiB
                    result?.max shouldBe sixGiB
                    result?.avg shouldBe 4L * 1024 * 1024 * 1024
                }
            }

            `when`("the average does not land on a whole byte") {
                val monitor = createDockerMonitor(monitorRepository)
                listOf(100L, 101L).forEach {
                    metricsLogRepository.insertLog(monitor.id, latencyMs = 5, stats = stats(1.0, it))
                }

                then("it should be rounded to one, because a fractional byte count is meaningless") {
                    metricsLogRepository.getMemoryUsageMetrics(monitor.id, PERIOD)?.avg shouldBe 101L
                }
            }

            `when`("there is no log at all") {
                val monitor = createDockerMonitor(monitorRepository)

                then("it should return null") {
                    metricsLogRepository.getMemoryUsageMetrics(monitor.id, PERIOD).shouldBeNull()
                }
            }
        }

        given("the inherited getLatencyMetrics() method") {

            `when`("there are logs with a latency") {
                val monitor = createDockerMonitor(monitorRepository)
                listOf(10, 20, 30, 40).forEach {
                    metricsLogRepository.insertLog(monitor.id, latencyMs = it, stats = null)
                }

                val result = metricsLogRepository.getLatencyMetrics(monitor.id, PERIOD)

                then("it should report the average, the minimum and the maximum as whole milliseconds") {
                    result?.avg shouldBe 25
                    result?.min shouldBe 10
                    result?.max shouldBe 40
                }

                // The interpolated p95 lands on 38.5, and Postgres rounds a double precision half to even
                then("it should report the percentiles too, unlike the CPU and memory aggregations") {
                    result?.p90 shouldBe 37
                    result?.p95 shouldBe 38
                    result?.p99 shouldBe 40
                }
            }
        }
    }

    private fun insertLogAt(monitorId: Long, cpu: BigDecimal, minutesAgo: Long) {
        dslContext.insertInto(DOCKER_METRICS_LOG)
            .set(
                DockerMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(5)
                    .setCpuUsagePercent(cpu)
                    .setCreatedAt(getCurrentTimestamp().minusMinutes(minutesAgo))
            )
            .execute()
    }
}
