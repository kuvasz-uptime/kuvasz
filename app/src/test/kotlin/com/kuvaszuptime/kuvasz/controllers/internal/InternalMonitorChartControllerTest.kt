package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.TCP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.ChartIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerRestartPointDto
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private const val CHARTS_PATH = "/api/internal/monitor-charts"
private const val OTHER_ZONE_OFFSET_HOURS = 5

@MicronautTest
class InternalMonitorChartControllerTest(
    private val client: InternalMonitorChartClient,
    @Client("/") private val httpClient: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val httpLatencyLogRepository: HttpLatencyLogRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the chart of an HTTP monitor") {

            `when`("the monitor has latency logs and incidents before, during and after the period") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                listOf(5L to 500, 3L to 100, 2L to 300).forEach { (hours, latency) ->
                    httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, latency, hoursAgo(hours))
                }
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 50, getCurrentTimestamp())
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = hoursAgo(6),
                    endedAt = hoursAgo(5),
                    error = "before the period",
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = hoursAgo(4),
                    endedAt = hoursAgo(3),
                    error = "Connection refused",
                )
                val from = hoursAgo(4)
                val to = hoursAgo(1)

                then("every check of the period is returned as it is, the oldest first, with the stats of the period") {
                    val chart = client.getHttpMonitorChart(monitor.id, period = null, from, to)

                    chart.series.timestamps shouldBe listOf(hoursAgo(3), hoursAgo(2)).map { it.toEpochMillis() }
                    chart.series.latencies shouldBe listOf(100, 300)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 200
                    chart.latencyStats.shouldNotBeNull().maxLatencyInMs shouldBe 300
                }

                then("the incidents open during the period are returned too") {
                    val incidents = client.getHttpMonitorChart(monitor.id, period = null, from, to).incidents

                    incidents shouldBe listOf(
                        ChartIncidentDto(hoursAgo(4).toEpochMillis(), hoursAgo(3).toEpochMillis(), "Connection refused")
                    )
                }

                then("a period is counted back from now") {
                    val chart = client.getHttpMonitorChart(monitor.id, Duration.ofHours(4), from = null, to = null)

                    chart.series.latencies shouldBe listOf(100, 300, 50)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 150
                }

                then("the period can be given in any time zone") {
                    val inAnotherZone = from.withOffsetSameInstant(ZoneOffset.ofHours(OTHER_ZONE_OFFSET_HOURS))

                    val chart = client.getHttpMonitorChart(monitor.id, period = null, inAnotherZone, to)

                    chart.series.latencies shouldBe listOf(100, 300)
                }
            }

            `when`("the latency history of the monitor is turned off") {
                val monitor = createHttpMonitor(httpMonitorRepository, latencyHistoryEnabled = false)
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 100)

                then("the chart is empty") {
                    val chart = client.getHttpMonitorChart(monitor.id, Duration.ofHours(1), from = null, to = null)

                    chart.series.timestamps.shouldBeEmpty()
                    chart.series.latencies.shouldBeEmpty()
                    chart.incidents.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }

            `when`("there is no monitor with the given ID") {
                val response = shouldThrow<HttpClientResponseException> {
                    httpClient.exchange("$CHARTS_PATH/http/424242?period=P1D").awaitFirst()
                }

                then("it returns a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("the period is not given properly") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val queries = listOf(
                    "",
                    "?from=2026-10-22T10:00:00Z",
                    "?to=2026-10-22T10:00:00Z",
                    "?period=P1D&from=2026-10-21T10:00:00Z&to=2026-10-22T10:00:00Z",
                    "?period=-P1D",
                    "?from=2026-10-22T10:00:00Z&to=2026-10-22T10:00:00Z",
                    "?period=PT9999999999999H",
                    "?period=P36501D",
                    "?from=1900-01-01T00:00:00Z&to=2100-01-01T00:00:00Z",
                )

                then("it returns a 400") {
                    queries.forEach { query ->
                        val response = shouldThrow<HttpClientResponseException> {
                            httpClient.exchange("$CHARTS_PATH/http/${monitor.id}$query").awaitFirst()
                        }
                        response.status shouldBe HttpStatus.BAD_REQUEST
                    }
                }
            }
        }

        given("the chart of a TCP monitor") {

            `when`("the monitor has metrics logs, some of a failed check") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                insertTcpLog(monitor.id, hoursAgo(2), latencyMs = 30)
                insertTcpLog(monitor.id, hoursAgo(1), latencyMs = null)

                then("the failed check is a gap in the series") {
                    val chart = client.getTcpMonitorChart(monitor.id, Duration.ofHours(3), from = null, to = null)

                    chart.series.latencies shouldBe listOf(30, null)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 30
                }
            }

            `when`("the period is long, so the checks are grouped into buckets") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val hour = now.truncatedTo(ChronoUnit.HOURS).minusHours(5)
                insertTcpLog(monitor.id, hour.minusDays(40), latencyMs = 999)
                insertTcpLog(monitor.id, hour.plusMinutes(10), latencyMs = 10)
                insertTcpLog(monitor.id, hour.plusMinutes(20), latencyMs = null)
                insertTcpLog(monitor.id, hour.plusMinutes(30), latencyMs = 30)
                insertTcpLog(monitor.id, hour.plusHours(1).plusMinutes(10), latencyMs = null)

                val chart = client.getTcpMonitorChart(monitor.id, Duration.ofDays(30), from = null, to = null)

                then("a bucket is an hour, and it tells how many of its checks have no reading") {
                    chart.series.checks shouldBe listOf(3, 1)
                    chart.series.checksWithoutReading shouldBe listOf(1, 1)
                }

                then("a point holds the average, the lowest and the highest reading, a gap if there is none") {
                    chart.series.latencies shouldBe listOf(20, null)
                    chart.series.lowestLatencies shouldBe listOf(10, null)
                    chart.series.highestLatencies shouldBe listOf(30, null)
                }

                then("the stats are still those of every check of the period") {
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 20
                    chart.latencyStats.shouldNotBeNull().minLatencyInMs shouldBe 10
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createTcpMonitor(tcpMonitorRepository, metricsHistoryEnabled = false)
                insertTcpLog(monitor.id, getCurrentTimestamp(), latencyMs = 30)

                then("the chart is empty") {
                    val chart = client.getTcpMonitorChart(monitor.id, Duration.ofHours(1), from = null, to = null)

                    chart.series.timestamps.shouldBeEmpty()
                    chart.series.checks.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }
        }

        given("the chart of a DNS monitor") {

            `when`("the monitor has metrics logs") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                insertDnsLog(monitor.id, hoursAgo(2), latencyMs = 10)
                insertDnsLog(monitor.id, hoursAgo(1), latencyMs = 20)

                then("every check of the period is returned") {
                    val chart = client.getDnsMonitorChart(monitor.id, Duration.ofHours(3), from = null, to = null)

                    chart.series.latencies shouldBe listOf(10, 20)
                    chart.latencyStats.shouldNotBeNull().minLatencyInMs shouldBe 10
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createDnsMonitor(dnsMonitorRepository, metricsHistoryEnabled = false)
                insertDnsLog(monitor.id, getCurrentTimestamp(), latencyMs = 10)

                then("the chart is empty") {
                    val chart = client.getDnsMonitorChart(monitor.id, Duration.ofHours(1), from = null, to = null)

                    chart.series.timestamps.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }
        }

        given("the chart of an ICMP monitor") {

            `when`("the monitor has metrics logs and an incident") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                insertIcmpLog(monitor.id, hoursAgo(2), latencyMs = 10, packetLoss = 0)
                insertIcmpLog(monitor.id, hoursAgo(1), latencyMs = null, packetLoss = 100)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = hoursAgo(1),
                    endedAt = null,
                    error = "Every packet was lost",
                )

                then("both the latency and the packet loss are returned for every check, with the stats of both") {
                    val chart = client.getIcmpMonitorChart(monitor.id, Duration.ofHours(3), from = null, to = null)

                    chart.series.latencies shouldBe listOf(10, null)
                    chart.series.packetLossPercentages shouldBe listOf(0, 100)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 10
                    chart.packetLossStats.shouldNotBeNull().maxPacketLossPercentage shouldBe 100
                    chart.incidents.map { it.endedAt to it.details } shouldBe listOf(null to "Every packet was lost")
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, metricsHistoryEnabled = false)
                insertIcmpLog(monitor.id, getCurrentTimestamp(), latencyMs = 10, packetLoss = 0)

                then("the chart is empty") {
                    val chart = client.getIcmpMonitorChart(monitor.id, Duration.ofHours(1), from = null, to = null)

                    chart.series.timestamps.shouldBeEmpty()
                    chart.series.packetLossPercentages.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                    chart.packetLossStats.shouldBeNull()
                }
            }
        }

        given("the chart of a Docker monitor") {

            `when`("the container was sampled, and it was restarted in the meantime") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val container = getCurrentTimestamp().minusDays(3).truncatedTo(ChronoUnit.SECONDS)
                insertDockerLog(monitor.id, hoursAgo(3), BigDecimal("10.00"), memory = 100, 0, container)
                insertDockerLog(monitor.id, hoursAgo(2), BigDecimal("30.00"), memory = 300, 0, container)
                insertDockerLog(monitor.id, hoursAgo(1), BigDecimal("20.00"), memory = 200, 1, container)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = hoursAgo(2),
                    endedAt = hoursAgo(1),
                    error = "The container exited (137)",
                    image = "nginx:1.27",
                )

                then("every reading of the period is returned, with the stats of the period") {
                    val chart = client.getDockerMonitorChart(monitor.id, Duration.ofHours(4), from = null, to = null)

                    chart.series.cpuUsagePercentages.map { it.shouldNotBeNull().toDouble() } shouldBe
                        listOf(10.0, 30.0, 20.0)
                    chart.series.memoryUsageBytes shouldBe listOf(100L, 300L, 200L)
                    chart.series.memoryLimitBytes shouldBe listOf(1_000L, 1_000L, 1_000L)
                    chart.cpuStats.shouldNotBeNull().maxCpuUsagePercentage.shouldNotBeNull() shouldBeEqualComparingTo
                        BigDecimal("30.00")
                    chart.memoryStats.shouldNotBeNull().minMemoryUsageBytes shouldBe 100
                }

                then("only the first check and the restart are returned as restart points") {
                    val chart = client.getDockerMonitorChart(monitor.id, Duration.ofHours(4), from = null, to = null)

                    chart.restarts shouldBe listOf(
                        DockerRestartPointDto(hoursAgo(3).toEpochMillis(), 0, container.toEpochMillis()),
                        DockerRestartPointDto(hoursAgo(1).toEpochMillis(), 1, container.toEpochMillis()),
                    )
                }

                then("the incidents carry the image of the container") {
                    val chart = client.getDockerMonitorChart(monitor.id, Duration.ofHours(4), from = null, to = null)

                    chart.incidents.map { it.details } shouldBe listOf("The container exited (137) · Image: nginx:1.27")
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createDockerMonitor(dockerMonitorRepository, metricsHistoryEnabled = false)

                then("the chart is empty") {
                    val chart = client.getDockerMonitorChart(monitor.id, Duration.ofHours(1), from = null, to = null)

                    chart.series.timestamps.shouldBeEmpty()
                    chart.restarts.shouldBeEmpty()
                    chart.cpuStats.shouldBeNull()
                    chart.memoryStats.shouldBeNull()
                }
            }
        }
    }

    // A single instant in whole seconds, so the timestamps can be compared with the epoch milliseconds of the response
    private val now = getCurrentTimestamp().truncatedTo(ChronoUnit.SECONDS)

    private fun hoursAgo(hours: Long): OffsetDateTime = now.minusHours(hours)

    private fun OffsetDateTime.toEpochMillis(): Long = toInstant().toEpochMilli()

    private fun insertTcpLog(monitorId: Long, createdAt: OffsetDateTime, latencyMs: Int?) {
        dslContext.insertInto(TCP_METRICS_LOG)
            .set(TcpMetricsLogRecord().setMonitorId(monitorId).setLatencyMs(latencyMs).setCreatedAt(createdAt))
            .execute()
    }

    private fun insertDnsLog(monitorId: Long, createdAt: OffsetDateTime, latencyMs: Int?) {
        dslContext.insertInto(DNS_METRICS_LOG)
            .set(DnsMetricsLogRecord().setMonitorId(monitorId).setLatencyMs(latencyMs).setCreatedAt(createdAt))
            .execute()
    }

    private fun insertIcmpLog(monitorId: Long, createdAt: OffsetDateTime, latencyMs: Int?, packetLoss: Int) {
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

    private fun insertDockerLog(
        monitorId: Long,
        createdAt: OffsetDateTime,
        cpu: BigDecimal,
        memory: Long,
        restartCount: Int,
        containerCreatedAt: OffsetDateTime,
    ) {
        dslContext.insertInto(DOCKER_METRICS_LOG)
            .set(
                DockerMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(5)
                    .setCpuUsagePercent(cpu)
                    .setMemoryUsageBytes(memory)
                    .setMemoryLimitBytes(1_000)
                    .setRestartCount(restartCount)
                    .setContainerCreatedAt(containerCreatedAt)
                    .setCreatedAt(createdAt)
            )
            .execute()
    }
}
