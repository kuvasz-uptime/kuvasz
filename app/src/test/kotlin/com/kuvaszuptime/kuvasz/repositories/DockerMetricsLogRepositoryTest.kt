package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerRestartPointDto
import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val PERIOD = Duration.ofHours(1)

// Narrower than the distance of the test checks, so each check is a bucket of its own
private val ONE_SECOND = Duration.ofSeconds(1)
private val ONE_HOUR = Duration.ofHours(1)

private fun stats(cpu: Double?, memory: Long?) = DockerContainerStats(
    cgroupVersion = DockerCgroupVersion.V2,
    cpuUsagePercent = cpu,
    memoryUsageBytes = memory,
    memoryLimitBytes = 8L * 1024 * 1024 * 1024,
)

/**
 * Covers the queries the shared [MonitorMetricsLogRepository] runs, which every other type's spec stubs out.
 * The Docker log is the one that exercises all three column types the aggregation has to cope with: an `Int` latency,
 * a `Long` memory reading that overflows an `Int`, and a fractional CPU percentage that must not be rounded to whole
 * units. The reads and the deletions are checked with the logs of several monitors on both sides of the time limit,
 * which is what the (monitor_id, created_at) indexes of the log tables are built for.
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
                insertLogAt(monitor.id, createdAt = minutesAgo(120), cpu = BigDecimal("10.00"))

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

            `when`("there are logs of other monitors and logs older than the period too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), latencyMs = 10)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), latencyMs = 20)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), latencyMs = 1_000)
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(10), latencyMs = 5_000)

                then("it should aggregate only the logs of the given monitor within the period") {
                    val result = metricsLogRepository.getLatencyMetrics(monitor.id, PERIOD)

                    result?.monitorId shouldBe monitor.id
                    result?.avg shouldBe 15
                    result?.min shouldBe 10
                    result?.max shouldBe 20
                }
            }
        }

        given("the inherited getAverageLatencies() method") {

            `when`("there are logs of several monitors, some of them older than the period") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                val monitorWithOldLogsOnly = createDockerMonitor(monitorRepository)
                val monitorOutsideTheQuery = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), latencyMs = 10)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), latencyMs = 15)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), latencyMs = 1_000)
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(5), latencyMs = 40)
                insertLogAt(monitorWithOldLogsOnly.id, createdAt = minutesAgo(120), latencyMs = 50)
                insertLogAt(monitorOutsideTheQuery.id, createdAt = minutesAgo(5), latencyMs = 60)

                val result = metricsLogRepository.getAverageLatencies(
                    monitorIds = listOf(monitor.id, otherMonitor.id, monitorWithOldLogsOnly.id),
                    period = PERIOD,
                )

                then("it should average the latencies of the given monitors within the period, in whole milliseconds") {
                    // 12.5 is rounded half up by Postgres for the numeric average
                    result shouldBe mapOf(monitor.id to 13, otherMonitor.id to 40)
                }
            }

            `when`("no monitor is given") {

                then("it should return an empty map without querying anything") {
                    metricsLogRepository.getAverageLatencies(emptyList(), PERIOD) shouldBe emptyMap()
                }
            }
        }

        given("the inherited fetchLatestByMonitorId() method") {

            `when`("there are logs of other monitors and logs older than the period too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                val oldest = insertLogAt(monitor.id, createdAt = minutesAgo(120))
                val older = insertLogAt(monitor.id, createdAt = minutesAgo(30))
                val newest = insertLogAt(monitor.id, createdAt = minutesAgo(10))
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(5))

                then("it should return only the logs of the given monitor within the period, the newest first") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id, PERIOD).map { it.id } shouldBe
                        listOf(newest, older)
                }

                then("without a period, it should return every log of the given monitor, the newest first") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).map { it.id } shouldBe
                        listOf(newest, older, oldest)
                }
            }
        }

        given("the inherited fetchLatencySeries() method") {

            `when`("there are logs before, during and after the period, of other monitors too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), latencyMs = 1)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), latencyMs = 20)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), latencyMs = 10)
                insertLogAt(monitor.id, createdAt = minutesAgo(1), latencyMs = 99)
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(5), latencyMs = 30)

                then("every log of the monitor between the start and the end is returned, the oldest first") {
                    val series = metricsLogRepository
                        .fetchLatencySeries(monitor.id, minutesAgo(60), minutesAgo(5), ONE_SECOND)

                    series.latencies shouldBe listOf(20, 10)
                    series.timestamps shouldBe listOf(minutesAgo(30), minutesAgo(10)).map { it.toEpochMillis() }
                }

                then("an open end of the period is not limited") {
                    metricsLogRepository.fetchLatencySeries(monitor.id, minutesAgo(60), null, ONE_SECOND)
                        .latencies shouldBe listOf(20, 10, 99)
                }
            }

            `when`("there is no log in the period") {
                val monitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120))

                then("the series are empty") {
                    val series = metricsLogRepository.fetchLatencySeries(monitor.id, minutesAgo(60), null, ONE_SECOND)

                    series.timestamps.shouldBeEmpty()
                    series.latencies.shouldBeEmpty()
                }
            }

            `when`("the logs are grouped into buckets") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                val bucket = now.truncatedTo(ChronoUnit.HOURS).minusHours(3)
                insertLogAt(monitor.id, createdAt = bucket.minusMinutes(5), latencyMs = 1_000)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(10), latencyMs = 10)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(20), latencyMs = 21)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(50), latencyMs = 30)
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(70), latencyMs = 7)
                insertLogAt(otherMonitor.id, createdAt = bucket.plusMinutes(15), latencyMs = 5_000)

                val series = metricsLogRepository
                    .fetchLatencySeries(monitor.id, bucket, bucket.plusHours(2), ONE_HOUR)

                then("a point holds the average, the lowest and the highest reading of its bucket") {
                    series.latencies shouldBe listOf(20, 7)
                    series.lowestLatencies shouldBe listOf(10, 7)
                    series.highestLatencies shouldBe listOf(30, 7)
                }

                then("a point is where its checks are: at their average time, not at the start of its bucket") {
                    // The checks are at the 10th, 20th and 50th minute of the hour, 26 min 40 s on average
                    series.timestamps shouldBe listOf(
                        bucket.plusMinutes(26).plusSeconds(40).toEpochMillis(),
                        bucket.plusMinutes(70).toEpochMillis(),
                    )
                }
            }
        }

        given("the getLatencyMetricsBetween() method") {

            `when`("there are logs before, during and after the period") {
                val monitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), latencyMs = 1_000)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), latencyMs = 20)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), latencyMs = 40)
                insertLogAt(monitor.id, createdAt = minutesAgo(1), latencyMs = 3_000)

                then("only the logs of the period are aggregated, with the percentiles too") {
                    val result = metricsLogRepository
                        .getLatencyMetricsBetween(monitor.id, minutesAgo(60), minutesAgo(10))
                        .shouldNotBeNull()

                    result.avg shouldBe 30
                    result.min shouldBe 20
                    result.max shouldBe 40
                    result.p99.shouldNotBeNull()
                }
            }
        }

        given("the fetchDockerSeries() method") {

            `when`("some of the checks were sampled") {
                val monitor = createDockerMonitor(monitorRepository)
                val oneGiB = 1024L * 1024 * 1024
                insertLogAt(
                    monitor.id,
                    createdAt = minutesAgo(30),
                    cpu = BigDecimal("12.34"),
                    memory = 3 * oneGiB,
                    memoryLimit = 8 * oneGiB,
                )
                // The container wasn't sampled at this check
                insertLogAt(monitor.id, createdAt = minutesAgo(20))

                val series = metricsLogRepository.fetchDockerSeries(monitor.id, minutesAgo(60), null, ONE_SECOND)

                then("the CPU keeps its decimals, and the memory readings their full size") {
                    series.cpuUsagePercentages.first().shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("12.34")
                    series.memoryUsageBytes.first() shouldBe 3 * oneGiB
                    series.memoryLimitBytes.first() shouldBe 8 * oneGiB
                }

                then("a check without a sample is a gap in each series") {
                    series.timestamps shouldBe listOf(minutesAgo(30), minutesAgo(20)).map { it.toEpochMillis() }
                    series.cpuUsagePercentages.last().shouldBeNull()
                    series.memoryUsageBytes.last().shouldBeNull()
                    series.memoryLimitBytes.last().shouldBeNull()
                }
            }
        }

        given("the fetchDockerSeries() method with buckets") {

            `when`("a bucket has checks without a sample") {
                val monitor = createDockerMonitor(monitorRepository)
                val bucket = now.truncatedTo(ChronoUnit.HOURS).minusHours(3)
                insertLogAt(
                    monitor.id,
                    createdAt = bucket.plusMinutes(10),
                    cpu = BigDecimal("10.00"),
                    memory = 1_000,
                    memoryLimit = 8_000,
                )
                insertLogAt(
                    monitor.id,
                    createdAt = bucket.plusMinutes(20),
                    cpu = BigDecimal("20.00"),
                    memory = 3_000,
                    memoryLimit = 8_000,
                )
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(30))
                insertLogAt(monitor.id, createdAt = bucket.plusMinutes(70))

                val series = metricsLogRepository.fetchDockerSeries(monitor.id, bucket, bucket.plusHours(2), ONE_HOUR)

                then("the readings are summarized, and the checks without a sample are left out of them") {
                    series.cpuUsagePercentages.first().shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("15.00")
                    series.lowestCpuUsagePercentages.first().shouldNotBeNull() shouldBeEqualComparingTo
                        BigDecimal("10.00")
                    series.highestCpuUsagePercentages.first().shouldNotBeNull() shouldBeEqualComparingTo
                        BigDecimal("20.00")
                    series.memoryUsageBytes.first() shouldBe 2_000L
                    series.lowestMemoryUsageBytes.first() shouldBe 1_000L
                    series.highestMemoryUsageBytes.first() shouldBe 3_000L
                    series.memoryLimitBytes.first() shouldBe 8_000L
                }

                then("a bucket without any sample is a gap in every series") {
                    series.cpuUsagePercentages.last().shouldBeNull()
                    series.highestCpuUsagePercentages.last().shouldBeNull()
                    series.memoryUsageBytes.last().shouldBeNull()
                    series.highestMemoryUsageBytes.last().shouldBeNull()
                    series.memoryLimitBytes.last().shouldBeNull()
                }
            }
        }

        given("the getCpuUsageMetricsBetween() and getMemoryUsageMetricsBetween() methods") {

            `when`("there are readings before and during the period") {
                val monitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), cpu = BigDecimal("90.00"), memory = 9_000)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), cpu = BigDecimal("10.00"), memory = 1_000)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), cpu = BigDecimal("20.00"), memory = 2_000)

                then("only the readings of the period are aggregated") {
                    val cpu = metricsLogRepository.getCpuUsageMetricsBetween(monitor.id, minutesAgo(60), null)
                    val memory = metricsLogRepository.getMemoryUsageMetricsBetween(monitor.id, minutesAgo(60), null)

                    cpu?.max.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("20.00")
                    memory?.avg shouldBe 1_500L
                }
            }
        }

        given("the fetchRestarts() method") {

            `when`("the restart count or the container changes between the checks") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                val container = getCurrentTimestamp().minusDays(10).truncatedTo(ChronoUnit.SECONDS)
                val recreatedContainer = minutesAgo(25)
                insertLogAt(monitor.id, createdAt = minutesAgo(120), restartCount = 9, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(50), restartCount = null)
                insertLogAt(monitor.id, minutesAgo(45), restartCount = 1, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(40), restartCount = 1, containerCreatedAt = container)
                insertLogAt(monitor.id, minutesAgo(35), restartCount = 2, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), restartCount = null, containerCreatedAt = container)
                insertLogAt(monitor.id, minutesAgo(20), restartCount = 2, containerCreatedAt = recreatedContainer)
                insertLogAt(monitor.id, minutesAgo(10), restartCount = 2, containerCreatedAt = recreatedContainer)
                insertLogAt(monitor.id, minutesAgo(1), restartCount = 3, containerCreatedAt = recreatedContainer)
                insertLogAt(otherMonitor.id, minutesAgo(15), restartCount = 5, containerCreatedAt = container)

                val restarts = metricsLogRepository.fetchRestarts(monitor.id, minutesAgo(60), minutesAgo(5))

                then("the last check with a count before the period, and the changes are returned, oldest first") {
                    restarts shouldBe listOf(
                        DockerRestartPointDto(minutesAgo(120).toEpochMillis(), 9, container.toEpochMillis()),
                        DockerRestartPointDto(minutesAgo(45).toEpochMillis(), 1, container.toEpochMillis()),
                        DockerRestartPointDto(minutesAgo(35).toEpochMillis(), 2, container.toEpochMillis()),
                        DockerRestartPointDto(minutesAgo(20).toEpochMillis(), 2, recreatedContainer.toEpochMillis()),
                    )
                }

                then("without a check with a count before the period, the first one of the period is the baseline") {
                    metricsLogRepository.fetchRestarts(otherMonitor.id, minutesAgo(60), null) shouldBe listOf(
                        DockerRestartPointDto(minutesAgo(15).toEpochMillis(), 5, container.toEpochMillis()),
                    )
                }
            }

            `when`("the container was restarted right before the first check of the period") {
                val monitor = createDockerMonitor(monitorRepository)
                val container = getCurrentTimestamp().minusDays(10).truncatedTo(ChronoUnit.SECONDS)
                insertLogAt(monitor.id, createdAt = minutesAgo(90), restartCount = 2, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(70), restartCount = 3, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(65), restartCount = null)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), restartCount = 4, containerCreatedAt = container)
                insertLogAt(monitor.id, createdAt = minutesAgo(20), restartCount = 4, containerCreatedAt = container)

                val restarts = metricsLogRepository.fetchRestarts(monitor.id, minutesAgo(60), null)

                then("the restart is returned after the last check with a count before the period") {
                    restarts shouldBe listOf(
                        DockerRestartPointDto(minutesAgo(70).toEpochMillis(), 3, container.toEpochMillis()),
                        DockerRestartPointDto(minutesAgo(30).toEpochMillis(), 4, container.toEpochMillis()),
                    )
                }
            }

            `when`("no check of the period has a restart count") {
                val monitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(10))

                then("there are no restart points") {
                    metricsLogRepository.fetchRestarts(monitor.id, minutesAgo(60), null).shouldBeEmpty()
                }
            }
        }

        given("the inherited fetchLastByMonitorId() method") {

            `when`("there are logs of other monitors too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(30))
                val newest = insertLogAt(monitor.id, createdAt = minutesAgo(10))
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(1))

                then("it should return the newest log of the given monitor") {
                    metricsLogRepository.fetchLastByMonitorId(monitor.id)?.id shouldBe newest
                }
            }

            `when`("two logs of the monitor were created at the very same time") {
                val monitor = createDockerMonitor(monitorRepository)
                val createdAt = minutesAgo(10)
                insertLogAt(monitor.id, createdAt = createdAt)
                val insertedLater = insertLogAt(monitor.id, createdAt = createdAt)

                then("it should return the one that was inserted later") {
                    metricsLogRepository.fetchLastByMonitorId(monitor.id)?.id shouldBe insertedLater
                }
            }

            `when`("the monitor has no log at all") {
                val monitor = createDockerMonitor(monitorRepository)

                then("it should return null") {
                    metricsLogRepository.fetchLastByMonitorId(monitor.id).shouldBeNull()
                }
            }
        }

        given("the fetchLastLatencyByMonitorId() method") {

            `when`("there are logs of other monitors too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(30), latencyMs = 30)
                insertLogAt(monitor.id, createdAt = minutesAgo(10), latencyMs = 10)
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(1), latencyMs = 1)

                then("it should return the latency of the newest log of the given monitor") {
                    metricsLogRepository.fetchLastLatencyByMonitorId(monitor.id) shouldBe 10
                }
            }

            `when`("the monitor has no log at all") {
                val monitor = createDockerMonitor(monitorRepository)

                then("it should return null") {
                    metricsLogRepository.fetchLastLatencyByMonitorId(monitor.id).shouldBeNull()
                }
            }
        }

        given("the inherited deleteAllByMonitorId() method") {

            `when`("there are logs of other monitors too") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120))
                insertLogAt(monitor.id, createdAt = minutesAgo(10))
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(10))

                val deleted = metricsLogRepository.deleteAllByMonitorId(monitor.id)

                then("it should delete every log of the given monitor, but only those") {
                    deleted shouldBe 2
                    countLogsOf(monitor.id) shouldBe 0
                    countLogsOf(otherMonitor.id) shouldBe 1
                }
            }
        }

        given("the inherited deleteLogsBeforeDate() method") {

            `when`("several monitors have logs on both sides of the limit") {
                val monitor = createDockerMonitor(monitorRepository)
                val otherMonitor = createDockerMonitor(monitorRepository)
                insertLogAt(monitor.id, createdAt = minutesAgo(120))
                val kept = insertLogAt(monitor.id, createdAt = minutesAgo(10))
                insertLogAt(otherMonitor.id, createdAt = minutesAgo(90))
                val otherKept = insertLogAt(otherMonitor.id, createdAt = minutesAgo(5))

                val deleted = metricsLogRepository.deleteLogsBeforeDate(minutesAgo(60))

                then("it should delete the logs older than the limit of every monitor, and keep the newer ones") {
                    deleted shouldBe 2
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).map { it.id } shouldBe listOf(kept)
                    metricsLogRepository.fetchLatestByMonitorId(otherMonitor.id).map { it.id } shouldBe
                        listOf(otherKept)
                }
            }
        }
    }

    // A single instant in whole seconds, so the timestamps can be compared with the epoch milliseconds of the series
    private val now = getCurrentTimestamp().truncatedTo(ChronoUnit.SECONDS)

    private fun minutesAgo(minutes: Long): OffsetDateTime = now.minusMinutes(minutes)

    private fun OffsetDateTime.toEpochMillis(): Long = toInstant().toEpochMilli()

    private fun insertLogAt(
        monitorId: Long,
        createdAt: OffsetDateTime,
        cpu: BigDecimal? = null,
        latencyMs: Int = 5,
        memory: Long? = null,
        memoryLimit: Long? = null,
        restartCount: Int? = null,
        containerCreatedAt: OffsetDateTime? = null,
    ): Long = checkNotNull(
        dslContext.insertInto(DOCKER_METRICS_LOG)
            .set(
                DockerMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setCpuUsagePercent(cpu)
                    .setMemoryUsageBytes(memory)
                    .setMemoryLimitBytes(memoryLimit)
                    .setRestartCount(restartCount)
                    .setContainerCreatedAt(containerCreatedAt)
                    .setCreatedAt(createdAt)
            )
            .returning(DOCKER_METRICS_LOG.ID)
            .fetchSingle(DOCKER_METRICS_LOG.ID)
    )

    private fun countLogsOf(monitorId: Long): Int =
        dslContext.fetchCount(DOCKER_METRICS_LOG, DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
