package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.Tables.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.ICMP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.Tables.TCP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
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
import java.time.temporal.ChronoUnit

private val ONE_DAY = Duration.ofDays(1)
private val SEVEN_DAYS = Duration.ofDays(7)
private val THIRTY_DAYS = Duration.ofDays(30)

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

            `when`("the monitor has latency logs in the last hours") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val hour = hourStartAgo(3)
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 100, hour.plusMinutes(5))
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 300, hour.plusMinutes(50))
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 50, getCurrentTimestamp().minusMinutes(1))

                then("a period below 7 days returns every check, with the stats of the period") {
                    val chart = client.getHttpMonitorChart(monitor.id, ONE_DAY)

                    chart.points.map { it.latencyInMs } shouldBe listOf(50, 300, 100)
                    chart.points.map { it.maxLatencyInMs } shouldBe listOf(null, null, null)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 150
                    chart.latencyStats.shouldNotBeNull().maxLatencyInMs shouldBe 300
                }

                then("7 days group the checks into quarters of an hour") {
                    val chart = client.getHttpMonitorChart(monitor.id, SEVEN_DAYS)

                    chart.points.map { it.latencyInMs to it.maxLatencyInMs } shouldBe
                        listOf(50 to 50, 300 to 300, 100 to 100)
                    chart.points.last().createdAt.toInstant() shouldBe hour.toInstant()
                }

                then("30 days group the checks into hours, with the average and the highest latency of each") {
                    val chart = client.getHttpMonitorChart(monitor.id, THIRTY_DAYS)

                    chart.points.map { it.latencyInMs to it.maxLatencyInMs } shouldBe listOf(50 to 50, 200 to 300)
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 150
                }
            }

            `when`("the latency history of the monitor is turned off") {
                val monitor = createHttpMonitor(httpMonitorRepository, latencyHistoryEnabled = false)
                httpLatencyLogRepository.insertLatencyForMonitor(monitor.id, 100)

                then("the chart is empty") {
                    val chart = client.getHttpMonitorChart(monitor.id, ONE_DAY)

                    chart.points.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }

            `when`("there is no monitor with the given ID") {
                val response = shouldThrow<HttpClientResponseException> {
                    httpClient.exchange("/api/internal/monitor-charts/http/424242?period=P1D").awaitFirst()
                }

                then("it returns a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the chart of a TCP monitor") {

            `when`("the monitor has metrics logs, some of a failed check") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val hour = hourStartAgo(2)
                insertTcpLog(monitor.id, hour.plusMinutes(10), latencyMs = 30)
                insertTcpLog(monitor.id, hour.plusMinutes(20), latencyMs = null)

                then("the bucket of the period leaves the failed checks out of the average and the highest latency") {
                    val chart = client.getTcpMonitorChart(monitor.id, THIRTY_DAYS)

                    chart.points.map { it.latencyInMs to it.maxLatencyInMs } shouldBe listOf(30 to 30)
                }

                then("the checks of a short period stay as they are, the failed one as a gap") {
                    client.getTcpMonitorChart(monitor.id, ONE_DAY).points.map { it.latencyInMs } shouldBe
                        listOf(null, 30)
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createTcpMonitor(tcpMonitorRepository, metricsHistoryEnabled = false)
                insertTcpLog(monitor.id, getCurrentTimestamp(), latencyMs = 30)

                then("the chart is empty") {
                    val chart = client.getTcpMonitorChart(monitor.id, ONE_DAY)

                    chart.points.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }
        }

        given("the chart of a DNS monitor") {

            `when`("the monitor has metrics logs") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val hour = hourStartAgo(2)
                insertDnsLog(monitor.id, hour.plusMinutes(1), latencyMs = 10)
                insertDnsLog(monitor.id, hour.plusMinutes(2), latencyMs = 20)

                then("30 days group them into hours") {
                    val chart = client.getDnsMonitorChart(monitor.id, THIRTY_DAYS)

                    chart.points.map { it.latencyInMs to it.maxLatencyInMs } shouldBe listOf(15 to 20)
                    chart.latencyStats.shouldNotBeNull().minLatencyInMs shouldBe 10
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createDnsMonitor(dnsMonitorRepository, metricsHistoryEnabled = false)
                insertDnsLog(monitor.id, getCurrentTimestamp(), latencyMs = 10)

                then("the chart is empty") {
                    val chart = client.getDnsMonitorChart(monitor.id, ONE_DAY)

                    chart.points.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                }
            }
        }

        given("the chart of an ICMP monitor") {

            `when`("the monitor has metrics logs") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val hour = hourStartAgo(2)
                insertIcmpLog(monitor.id, hour.plusMinutes(1), latencyMs = 10, packetLoss = 0)
                insertIcmpLog(monitor.id, hour.plusMinutes(2), latencyMs = null, packetLoss = 100)

                then("30 days group both the latency and the packet loss into hours, with the stats of both") {
                    val chart = client.getIcmpMonitorChart(monitor.id, THIRTY_DAYS)

                    val point = chart.points.single()
                    point.latencyInMs shouldBe 10
                    point.maxLatencyInMs shouldBe 10
                    point.packetLossPercentage shouldBe 50
                    point.maxPacketLossPercentage shouldBe 100
                    chart.latencyStats.shouldNotBeNull().averageLatencyInMs shouldBe 10
                    chart.packetLossStats.shouldNotBeNull().maxPacketLossPercentage shouldBe 100
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, metricsHistoryEnabled = false)
                insertIcmpLog(monitor.id, getCurrentTimestamp(), latencyMs = 10, packetLoss = 0)

                then("the chart is empty") {
                    val chart = client.getIcmpMonitorChart(monitor.id, ONE_DAY)

                    chart.points.shouldBeEmpty()
                    chart.latencyStats.shouldBeNull()
                    chart.packetLossStats.shouldBeNull()
                }
            }
        }

        given("the chart of a Docker monitor") {

            `when`("the container was sampled, and it was restarted in the meantime") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val hour = hourStartAgo(2)
                val container = getCurrentTimestamp().minusDays(3)
                insertDockerLog(monitor.id, hour.plusMinutes(1), BigDecimal("10.00"), memory = 100, 0, container)
                insertDockerLog(monitor.id, hour.plusMinutes(2), BigDecimal("30.00"), memory = 300, 0, container)
                insertDockerLog(monitor.id, hour.plusMinutes(3), BigDecimal("20.00"), memory = 200, 1, container)

                then("30 days group the readings into hours, and keep every restart exact") {
                    val chart = client.getDockerMonitorChart(monitor.id, THIRTY_DAYS)

                    val point = chart.points.single()
                    point.cpuUsagePercent.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("20.00")
                    point.maxCpuUsagePercent.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("30.00")
                    point.memoryUsageBytes shouldBe 200
                    point.maxMemoryUsageBytes shouldBe 300
                    chart.restarts.map { it.restartCount } shouldBe listOf(1, 0)
                    chart.cpuStats.shouldNotBeNull().maxCpuUsagePercentage.shouldNotBeNull() shouldBeEqualComparingTo
                        BigDecimal("30.00")
                    chart.memoryStats.shouldNotBeNull().minMemoryUsageBytes shouldBe 100
                }
            }

            `when`("the metrics history of the monitor is turned off") {
                val monitor = createDockerMonitor(dockerMonitorRepository, metricsHistoryEnabled = false)

                then("the chart is empty") {
                    val chart = client.getDockerMonitorChart(monitor.id, ONE_DAY)

                    chart.points.shouldBeEmpty()
                    chart.restarts.shouldBeEmpty()
                    chart.cpuStats.shouldBeNull()
                    chart.memoryStats.shouldBeNull()
                }
            }
        }
    }

    // The start of a whole hour, a few hours ago, so the logs can be placed into the buckets of the charts
    private fun hourStartAgo(hours: Long): OffsetDateTime =
        getCurrentTimestamp().truncatedTo(ChronoUnit.HOURS).minusHours(hours)

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
