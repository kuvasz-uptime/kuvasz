package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createIcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.randomClientSecret
import com.kuvaszuptime.kuvasz.models.ApiErrorCode
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.PingExecutor
import com.kuvaszuptime.kuvasz.services.check.icmp.PingResult
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.util.getBodyAs
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.subscribers.TestSubscriber
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest(environments = ["full-integrations-setup"])
class IcmpMonitorControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: IcmpMonitorClient,
    private val monitorRepository: IcmpMonitorRepository,
    private val latencyLogRepository: IcmpMetricsLogRepository,
    private val checkScheduler: IcmpCheckScheduler,
    private val statCalculator: StatCalculator,
    private val eventDispatcher: EventDispatcher,
    private val statusPageRepository: StatusPageRepository,
    private val appConfig: AppConfig,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    @MockBean(PingExecutor::class)
    fun pingExecutorMock(): PingExecutor = mockk {
        every { execute(any(), any(), any()) } returns PingResult(
            packetsSent = 3,
            packetsReceived = 3,
            packetLossPercentage = 0,
            avgLatencyMs = 10,
            rawOutput = "",
            isOutputRecognized = true,
        )
    }

    init {
        given("GET /api/v2/icmp-monitors/") {
            `when`("there are monitors in the database") {
                val monitor = createIcmpMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                val response = monitorClient.getMonitorsWithDetails(enabled = null, uptimeStatus = null)

                then("it should return them with details") {
                    response shouldHaveSize 1
                    val item = response.first()
                    item.id shouldBe monitor.id
                    item.name shouldBe monitor.name
                    item.host shouldBe monitor.host
                    item.uptimeCheckInterval shouldBe monitor.uptimeCheckInterval
                    item.packetCount shouldBe monitor.packetCount
                    item.timeoutSeconds shouldBe monitor.timeoutSeconds
                    item.packetLossThreshold shouldBe monitor.packetLossThreshold
                    item.failureCountThreshold shouldBe monitor.failureCountThreshold
                    item.metricsHistoryEnabled shouldBe true
                    item.enabled shouldBe monitor.enabled
                    item.uptimeStatus shouldBe UptimeStatus.UP
                }
            }

            `when`("filtering by enabled=false") {
                createIcmpMonitor(monitorRepository, enabled = true)
                createIcmpMonitor(monitorRepository, enabled = false)

                val response = monitorClient.getMonitorsWithDetails(enabled = false, uptimeStatus = null)

                then("only disabled monitors should be returned") {
                    response shouldHaveSize 1
                    response.first().enabled shouldBe false
                }
            }

            `when`("filtering by uptimeStatus=UP") {
                val upMonitor = createIcmpMonitor(monitorRepository)
                val downMonitor = createIcmpMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.DOWN,
                    endedAt = null
                )

                val response =
                    monitorClient.getMonitorsWithDetails(enabled = null, uptimeStatus = listOf(UptimeStatus.UP))

                then("only UP monitors should be returned") {
                    response shouldHaveSize 1
                    response.first().id shouldBe upMonitor.id
                }
            }
        }

        given("GET /api/v2/icmp-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createIcmpMonitor(monitorRepository)
                createMaintenanceWindow(
                    dslContext,
                    name = "active-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name)),
                )

                val response = monitorClient.getMonitorDetails(monitor.id)

                then("it should return monitor details") {
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.host shouldBe monitor.host
                    response.uptimeStatus.shouldBeNull()
                    response.maintenanceWindows.map { it.name } shouldBe listOf("active-window")
                    response.maintenanceWindows.single().active shouldBe true
                    response.inMaintenance shouldBe true
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/icmp-monitors/999999").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("POST /api/v2/icmp-monitors/") {
            `when`("a valid monitor is created") {
                val monitorName = randomClientSecret()
                val createDto = IcmpMonitorCreateDto(
                    name = monitorName,
                    host = "127.0.0.1",
                    uptimeCheckInterval = 60,
                    packetCount = IcmpMonitorDefaults.PACKET_COUNT,
                    timeoutSeconds = IcmpMonitorDefaults.TIMEOUT_SECONDS,
                    packetLossThreshold = IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD,
                    failureCountThreshold = IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/icmp-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should create the monitor and return 201") {
                    response.status shouldBe HttpStatus.CREATED
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    createdMonitor.host shouldBe "127.0.0.1"
                    createdMonitor.uptimeCheckInterval shouldBe 60L
                }

                then("it should schedule checks for the monitor") {
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    checkScheduler.getScheduledUptimeChecks().containsKey(createdMonitor.id) shouldBe true
                }
            }

            `when`("metricsHistoryEnabled is set to false when creating a monitor") {
                val monitorName = randomClientSecret()
                val createDto = IcmpMonitorCreateDto(
                    name = monitorName,
                    host = "127.0.0.1",
                    uptimeCheckInterval = 60,
                    packetCount = IcmpMonitorDefaults.PACKET_COUNT,
                    timeoutSeconds = IcmpMonitorDefaults.TIMEOUT_SECONDS,
                    packetLossThreshold = IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD,
                    failureCountThreshold = IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                    metricsHistoryEnabled = false,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/icmp-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should create the monitor with metricsHistoryEnabled=false and return 201") {
                    response.status shouldBe HttpStatus.CREATED
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    createdMonitor.metricsHistoryEnabled shouldBe false
                }
            }

            `when`("validation fails - blank host") {
                val request = HttpRequest.POST(
                    "/api/v2/icmp-monitors/",
                    mapOf(
                        "name" to "test",
                        "host" to " ",
                        "uptimeCheckInterval" to 60,
                        "packetCount" to 3,
                        "timeoutSeconds" to 5,
                        "packetLossThreshold" to 100,
                        "failureCountThreshold" to 1
                    )
                ).header("X-Api-Key", "test")

                val ex = io.kotest.assertions.throwables.shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("validation fails - packetCount out of range") {
                val request = HttpRequest.POST(
                    "/api/v2/icmp-monitors/",
                    mapOf(
                        "name" to "test",
                        "host" to "127.0.0.1",
                        "uptimeCheckInterval" to 60,
                        "packetCount" to 20,
                        "timeoutSeconds" to 5,
                        "packetLossThreshold" to 100,
                        "failureCountThreshold" to 1
                    )
                ).header("X-Api-Key", "test")

                val ex = io.kotest.assertions.throwables.shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        given("PATCH /api/v2/icmp-monitors/{id}") {
            `when`("a monitor is updated") {
                val monitor = createIcmpMonitor(monitorRepository, host = "1.1.1.1")

                val updateNode = mapper.createObjectNode().put("host", "8.8.8.8")
                val updatedMonitor = monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should update the monitor") {
                    updatedMonitor.host shouldBe "8.8.8.8"
                }

                then("it should reschedule checks") {
                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe true
                }
            }

            `when`("metricsHistoryEnabled is updated to false and there are existing metrics logs") {
                val monitor = createIcmpMonitor(monitorRepository, metricsHistoryEnabled = true)
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 100,
                    packetLossPercentage = 0
                )
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 200,
                    packetLossPercentage = 5
                )
                latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldNotBeNull()

                val updateNode = mapper.createObjectNode().put("metricsHistoryEnabled", false)
                monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should update the monitor and remove existing metrics log records") {
                    val monitorInDb = monitorRepository.findById(monitor.id, null).shouldNotBeNull()
                    monitorInDb.metricsHistoryEnabled shouldBe false
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("the monitor does not exist") {
                val updateNode = mapper.createObjectNode().put("host", "8.8.8.8")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(
                        HttpRequest.PATCH("/api/v2/icmp-monitors/999999", updateNode)
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called to update a monitor's name that is also present on a status page - writable") {
                val monitor1 = createIcmpMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createIcmpMonitor(monitorRepository, monitorName = "monitor2")
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.ICMP, monitor1.name),
                        MonitorID(MonitorType.ICMP, monitor2.name),
                    )
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor2.name))
                )

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(IcmpMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()
                val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()

                then("it should update the monitor and also update the monitor reference on the status pages") {
                    monitorInDb.name shouldBe "updated_monitor1"

                    statusPage1InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.ICMP, "updated_monitor1"),
                        MonitorID(MonitorType.ICMP, monitor2.name),
                    )
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.ICMP, monitor2.name),
                    )
                    statusPage2InDb.updatedAt shouldBe statusPage2InDb.createdAt
                }
            }

            `when`("it is called to update a monitor's name that is NOT present on a non-writable status page") {
                val monitor1 = createIcmpMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createIcmpMonitor(monitorRepository, monitorName = "monitor2")
                createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor2.name))
                )

                appConfig.disableStatusPageExternalWrite()

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(IcmpMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()

                then("it should update the monitor") {
                    monitorInDb.name shouldBe "updated_monitor1"
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called to update a monitor's name that is present on a non-writable status page") {
                val monitor = createIcmpMonitor(monitorRepository, monitorName = "monitor1")
                createStatusPage(
                    dslContext,
                    public = false,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name))
                )
                appConfig.disableStatusPageExternalWrite()
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(IcmpMonitorUpdateDto::name.name, "updated_monitor1")

                val ex = shouldThrow<HttpClientResponseException> {
                    monitorClient.updateMonitor(monitor.id, updateDto)
                }
                val monitorInDb = monitorRepository.findById(monitor.id, null).shouldNotBeNull()

                then("it should not let the update happen and return a 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    with(ex.response.getBodyAs<ServiceError>().shouldNotBeNull()) {
                        message shouldContain "The monitor's name cannot be changed, because it's already " +
                            "referenced in the YAML file by a status page."
                        errorCode shouldBe ApiErrorCode.MONITOR_NAME_CANNOT_BE_CHANGED
                    }
                    monitorInDb.name shouldBe monitor.name
                    appConfig.enableStatusPageExternalWrite()
                }
            }
        }

        given("DELETE /api/v2/icmp-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createIcmpMonitor(monitorRepository)

                monitorClient.deleteMonitor(monitor.id)

                then("it should delete the monitor") {
                    monitorRepository.findById(monitor.id, null).shouldBeNull()
                }

                then("it should remove scheduled checks") {
                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe false
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(
                        HttpRequest.DELETE<String>("/api/v2/icmp-monitors/999999")
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called with an existing monitor that belongs to more than one status page") {
                val monitor = createIcmpMonitor(monitorRepository, monitorName = "test_monitor")
                val anotherMonitor = createIcmpMonitor(monitorRepository, monitorName = "another_test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/icmp-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name))
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.ICMP, monitor.name),
                        MonitorID(MonitorType.ICMP, anotherMonitor.name),
                    )
                )
                val statusPageWithoutDeletedMonitor = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.ICMP, anotherMonitor.name))
                )
                delay(1000.milliseconds)

                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(monitor.id, null)

                then("it should delete the monitor from the status pages and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe false

                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.ICMP, monitor.id)

                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors.shouldBeEmpty()
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()
                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.ICMP, anotherMonitor.name)
                    )
                    statusPage2InDb.updatedAt shouldBeAfter statusPage2InDb.createdAt

                    val statusPage3InDb = statusPageRepository.findById(statusPageWithoutDeletedMonitor.id)
                        .shouldNotBeNull()
                    statusPage3InDb.monitors.shouldContainExactly(statusPageWithoutDeletedMonitor.monitors)
                    statusPage3InDb.updatedAt shouldBe statusPage3InDb.createdAt
                }
            }

            `when`("it is called with an existing monitor that belongs to a non-writable status page") {
                val monitor = createIcmpMonitor(monitorRepository, monitorName = "test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/icmp-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name))
                )
                delay(1000.milliseconds)
                appConfig.disableStatusPageExternalWrite()

                val ex = shouldThrow<HttpClientResponseException> { client.exchange(deleteRequest).awaitFirst() }
                val monitorInDb = monitorRepository.findById(monitor.id, null)

                then("it should reject the deletion and return a 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<ServiceError>()?.errorCode shouldBe ApiErrorCode.MONITOR_CANNOT_BE_DELETED
                    monitorInDb.shouldNotBeNull()

                    subscriber.assertNoValues()

                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors shouldBe statusPage1.monitors
                    statusPage1InDb.updatedAt shouldBe statusPage1InDb.createdAt
                    appConfig.enableStatusPageExternalWrite()
                }
            }
        }

        given("GET /api/v2/icmp-monitors/{id}/uptime-events") {
            `when`("there are events for the monitor") {
                val monitor = createIcmpMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusSeconds(60),
                    status = UptimeStatus.DOWN,
                    endedAt = now,
                )
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                val events = monitorClient.getUptimeEvents(monitor.id)

                then("it should return events in descending order") {
                    events shouldHaveSize 2
                    events.first().status shouldBe UptimeStatus.UP
                    events.last().status shouldBe UptimeStatus.DOWN
                }
            }
        }

        given("GET /api/v2/icmp-monitors/{id}/stats") {
            `when`("metrics log records are present") {
                val monitor = createIcmpMonitor(monitorRepository)
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 100,
                    packetLossPercentage = 0
                )
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 200,
                    packetLossPercentage = 10
                )
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 150,
                    packetLossPercentage = 5
                )

                then("it should return the correct latency stats, packet loss stats and latency logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalIcmpUptimeStats(
                            period = Duration.ofDays(1),
                            monitorId = monitor.id,
                        )
                    } returns HistoricalUptimeStatsDto(
                        period = Duration.ofDays(1).toString(),
                        incidents = 0,
                        affectedMonitors = 0,
                        uptimeRatio = 1.0,
                        totalDowntimeSeconds = 0,
                    )

                    val stats = monitorClient.getMonitorStats(monitor.id, period = null)
                    stats.id shouldBe monitor.id
                    stats.metricsHistoryEnabled shouldBe true
                    stats.uptimeHistory.incidents shouldBe 0
                    stats.uptimeHistory.uptimeRatio shouldBe 1.0
                    stats.uptimeHistory.period shouldBe "PT24H"

                    stats.latencyStats shouldNotBeNull {
                        averageLatencyInMs shouldBe 150
                        minLatencyInMs shouldBe 100
                        maxLatencyInMs shouldBe 200
                        p90LatencyInMs.shouldNotBeNull()
                        p95LatencyInMs.shouldNotBeNull()
                        p99LatencyInMs.shouldNotBeNull()
                    }
                    stats.packetLossStats shouldNotBeNull {
                        averagePacketLossPercentage shouldBe 5
                        minPacketLossPercentage shouldBe 0
                        maxPacketLossPercentage shouldBe 10
                        p90PacketLossPercentage.shouldNotBeNull()
                        p95PacketLossPercentage.shouldNotBeNull()
                        p99PacketLossPercentage.shouldNotBeNull()
                    }
                    stats.metricsLogs shouldHaveSize 3
                    // Latency logs should be sorted by their creation in descending order
                    stats.metricsLogs[0].id shouldBeGreaterThan stats.metricsLogs[1].id
                }
            }

            `when`("metrics log records outside the period are excluded") {
                val monitor = createIcmpMonitor(monitorRepository)
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 100,
                    packetLossPercentage = 0
                )
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 300,
                    packetLossPercentage = 0
                )
                // This record is outside the 4-minute period
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 600,
                    packetLossPercentage = 50,
                    createdAt = getCurrentTimestamp().minusMinutes(5),
                )

                then("only records within the period should be considered for latency and packet loss stats") {
                    val testPeriod = Duration.ofMinutes(4)
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalIcmpUptimeStats(
                            period = testPeriod,
                            monitorId = monitor.id,
                        )
                    } returns HistoricalUptimeStatsDto(
                        period = testPeriod.toString(),
                        incidents = 0,
                        affectedMonitors = 0,
                        uptimeRatio = 1.0,
                        totalDowntimeSeconds = 0,
                    )

                    val stats = monitorClient.getMonitorStats(monitor.id, period = testPeriod)
                    stats.metricsLogs shouldHaveSize 2
                    stats.latencyStats shouldNotBeNull {
                        averageLatencyInMs shouldBe 200
                        minLatencyInMs shouldBe 100
                        maxLatencyInMs shouldBe 300
                    }
                    stats.packetLossStats shouldNotBeNull {
                        averagePacketLossPercentage shouldBe 0
                        minPacketLossPercentage shouldBe 0
                        maxPacketLossPercentage shouldBe 0
                    }
                    stats.uptimeHistory.period shouldBe "PT4M"
                }
            }

            `when`("there are no metrics log records") {
                val monitor = createIcmpMonitor(monitorRepository)

                then("it should return null for latency and packet loss stats and an empty list for logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalIcmpUptimeStats(
                            period = Duration.ofDays(1),
                            monitorId = monitor.id,
                        )
                    } returns HistoricalUptimeStatsDto(
                        period = Duration.ofDays(1).toString(),
                        incidents = 0,
                        affectedMonitors = 0,
                        uptimeRatio = null,
                        totalDowntimeSeconds = 0,
                    )

                    val stats = monitorClient.getMonitorStats(monitor.id, period = null)
                    stats.id shouldBe monitor.id
                    stats.metricsHistoryEnabled shouldBe true
                    stats.latencyStats shouldBe null
                    stats.packetLossStats shouldBe null
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("metricsHistoryEnabled is false") {
                val monitor = createIcmpMonitor(monitorRepository, metricsHistoryEnabled = false)
                createIcmpMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 100,
                    packetLossPercentage = 0
                )

                then("it should return stats with metricsHistoryEnabled=false and no latency data") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalIcmpUptimeStats(
                            period = Duration.ofDays(1),
                            monitorId = monitor.id,
                        )
                    } returns HistoricalUptimeStatsDto(
                        period = Duration.ofDays(1).toString(),
                        incidents = 0,
                        affectedMonitors = 0,
                        uptimeRatio = null,
                        totalDowntimeSeconds = 0,
                    )

                    val stats = monitorClient.getMonitorStats(monitor.id, period = null)
                    stats.id shouldBe monitor.id
                    stats.metricsHistoryEnabled shouldBe false
                    stats.latencyStats shouldBe null
                    stats.packetLossStats shouldBe null
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/icmp-monitors/999999/stats").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("GET /api/v2/icmp-monitors/monitoring-stats") {

            val monitoringStatsDtoStub = IcmpMonitoringStatsDto(
                actual = IcmpMonitoringStatsDto.ActualMonitoringStats(
                    uptimeStats = ActualUptimeStats(
                        total = 10000,
                        down = 8185,
                        up = 3535,
                        paused = 7157,
                        inProgress = 6139,
                        inMaintenance = 2914,
                        lastIncident = getCurrentTimestamp()
                    )
                ),
                history = IcmpMonitoringStatsDto.HistoricalMonitoringStats(
                    uptimeStats = HistoricalUptimeStatsDto(
                        incidents = 7630,
                        affectedMonitors = 8313,
                        uptimeRatio = 0.12343784,
                        totalDowntimeSeconds = 123456789L,
                        period = Duration.ofDays(7).toString(),
                    )
                )
            )

            `when`("it's called without an explicit period") {

                val statCalculatorMock = getMock(statCalculator)
                every { statCalculatorMock.calculateOverallIcmpStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = null)

                then("it should delegate to the StatCalculator with the default period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallIcmpStats(Duration.ofHours(168)) }
                }
            }

            `when`("it's called with an explicit period") {

                val statCalculatorMock = getMock(statCalculator)
                every { statCalculatorMock.calculateOverallIcmpStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = Duration.ofDays(1))

                then("it should delegate to the StatCalculator with the explicit period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallIcmpStats(Duration.ofDays(1)) }
                }
            }
        }
    }

    @MockBean(StatCalculator::class)
    fun mockStatCalculator() = mockk<StatCalculator>()
}
