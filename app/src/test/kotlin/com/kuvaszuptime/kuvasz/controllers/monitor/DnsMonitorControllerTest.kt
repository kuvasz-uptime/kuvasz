package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.randomClientSecret
import com.kuvaszuptime.kuvasz.models.ApiErrorCode
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckResult
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.dns.DnsResolveExecutor
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
class DnsMonitorControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: DnsMonitorClient,
    private val monitorRepository: DnsMonitorRepository,
    private val latencyLogRepository: DnsMetricsLogRepository,
    private val checkScheduler: DnsCheckScheduler,
    private val statCalculator: StatCalculator,
    private val eventDispatcher: EventDispatcher,
    private val statusPageRepository: StatusPageRepository,
    private val appConfig: AppConfig,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    @MockBean(DnsResolveExecutor::class)
    fun resolveExecutorMock(): DnsResolveExecutor = mockk {
        every { execute(any(), any(), any(), any(), any(), any(), any()) } returns DnsCheckResult(
            records = mapOf(DnsRecordType.A to listOf("1.2.3.4")),
            responseCode = DnsResponseCode.NOERROR,
            latencyMs = 10,
            error = null,
        )
    }

    init {
        given("GET /api/v2/dns-monitors/") {
            `when`("there are monitors in the database") {
                val monitor = createDnsMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createDnsUptimeEventRecord(
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
                    item.timeoutMs shouldBe monitor.timeoutMs
                    item.latencyThresholdMs shouldBe monitor.latencyThresholdMs
                    item.failureCountThreshold shouldBe monitor.failureCountThreshold
                    item.metricsHistoryEnabled shouldBe true
                    item.enabled shouldBe monitor.enabled
                    item.uptimeStatus shouldBe UptimeStatus.UP
                }
            }

            `when`("filtering by enabled=false") {
                createDnsMonitor(monitorRepository, enabled = true)
                createDnsMonitor(monitorRepository, enabled = false)

                val response = monitorClient.getMonitorsWithDetails(enabled = false, uptimeStatus = null)

                then("only disabled monitors should be returned") {
                    response shouldHaveSize 1
                    response.first().enabled shouldBe false
                }
            }

            `when`("filtering by uptimeStatus=UP") {
                val upMonitor = createDnsMonitor(monitorRepository)
                val downMonitor = createDnsMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createDnsUptimeEventRecord(
                    dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createDnsUptimeEventRecord(
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

        given("GET /api/v2/dns-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createDnsMonitor(monitorRepository)
                createMaintenanceWindow(
                    dslContext,
                    name = "active-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor.name)),
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
                    client.exchange("/api/v2/dns-monitors/999999").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("POST /api/v2/dns-monitors/") {
            `when`("a monitor is created with every DNS-specific option set") {
                val monitorName = randomClientSecret()
                val matchers = listOf(
                    DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4"),
                    DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "v=spf1.*"),
                )
                val createDto = DnsMonitorCreateDto(
                    name = monitorName,
                    host = "example.com",
                    uptimeCheckInterval = 60,
                    resolverHost = "1.1.1.1",
                    resolverPort = 5353,
                    transport = DnsTransport.TCP,
                    recordMatchers = matchers,
                    driftDetectionEnabled = true,
                    driftRecordTypes = listOf(DnsRecordType.NS, DnsRecordType.MX),
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/dns-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("every DNS-specific field is persisted") {
                    response.status shouldBe HttpStatus.CREATED
                    val created = monitorRepository.findByName(monitorName).shouldNotBeNull()
                    created.resolverHost shouldBe "1.1.1.1"
                    created.resolverPort shouldBe 5353
                    created.transport shouldBe DnsTransport.TCP
                    created.expectedResponseCode shouldBe DnsResponseCode.NOERROR
                    created.driftDetectionEnabled shouldBe true
                    created.driftRecordTypes.toList() shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
                    created.recordMatchersAsList() shouldBe matchers
                }

                then("the details endpoint reads them back, decoding the JSONB matchers") {
                    val created = monitorRepository.findByName(monitorName).shouldNotBeNull()
                    val details = monitorClient.getMonitorDetails(created.id)
                    details.recordMatchers shouldBe matchers
                    details.driftRecordTypes shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
                    details.transport shouldBe DnsTransport.TCP
                    details.resolverPort shouldBe 5353
                }
            }

            `when`("a monitor is created with a non-NOERROR expected response code and no matchers") {
                val monitorName = randomClientSecret()
                val createDto = DnsMonitorCreateDto(
                    name = monitorName,
                    host = "does-not-exist.example.com",
                    uptimeCheckInterval = 60,
                    expectedResponseCode = DnsResponseCode.NXDOMAIN,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/dns-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it is accepted") {
                    response.status shouldBe HttpStatus.CREATED
                    monitorRepository.findByName(monitorName)
                        .shouldNotBeNull().expectedResponseCode shouldBe DnsResponseCode.NXDOMAIN
                }
            }

            `when`("a monitor is created with a non-NOERROR expected response code and matchers") {
                val request = HttpRequest.POST(
                    "/api/v2/dns-monitors/",
                    mapOf(
                        "name" to randomClientSecret(),
                        "host" to "example.com",
                        "uptimeCheckInterval" to 60,
                        "expectedResponseCode" to "NXDOMAIN",
                        "recordMatchers" to listOf(mapOf("recordType" to "A", "value" to "1.2.3.4")),
                    )
                ).header("X-Api-Key", "test")

                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it is rejected, because the two assertions contradict each other") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("a valid monitor is created") {
                val monitorName = randomClientSecret()
                val createDto = DnsMonitorCreateDto(
                    name = monitorName,
                    host = "127.0.0.1",
                    uptimeCheckInterval = 60,
                    timeoutMs = DnsMonitorDefaults.TIMEOUT_MS,
                    latencyThresholdMs = null,
                    failureCountThreshold = DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/dns-monitors/", createDto).header("X-Api-Key", "test"),
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
                val createDto = DnsMonitorCreateDto(
                    name = monitorName,
                    host = "127.0.0.1",
                    uptimeCheckInterval = 60,
                    timeoutMs = DnsMonitorDefaults.TIMEOUT_MS,
                    latencyThresholdMs = null,
                    failureCountThreshold = DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                    metricsHistoryEnabled = false,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/dns-monitors/", createDto).header("X-Api-Key", "test"),
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
                    "/api/v2/dns-monitors/",
                    mapOf(
                        "name" to "test",
                        "host" to " ",
                        "uptimeCheckInterval" to 60,
                        "timeoutMs" to 5000,
                        "failureCountThreshold" to 1
                    )
                ).header("X-Api-Key", "test")

                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("validation fails - resolverPort out of range") {
                val request = HttpRequest.POST(
                    "/api/v2/dns-monitors/",
                    mapOf(
                        "name" to "test",
                        "host" to "example.com",
                        "resolverPort" to 70000,
                        "uptimeCheckInterval" to 60,
                        "timeoutMs" to 5000,
                        "failureCountThreshold" to 1
                    )
                ).header("X-Api-Key", "test")

                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("validation fails - latencyThresholdMs is not positive") {
                val request = HttpRequest.POST(
                    "/api/v2/dns-monitors/",
                    mapOf(
                        "name" to "test",
                        "host" to "127.0.0.1",
                        "uptimeCheckInterval" to 60,
                        "timeoutMs" to 5000,
                        "latencyThresholdMs" to 0,
                        "failureCountThreshold" to 1
                    )
                ).header("X-Api-Key", "test")

                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("a monitor with an already-taken name is created") {
                val existing = createDnsMonitor(monitorRepository)
                val createDto = DnsMonitorCreateDto(
                    name = existing.name,
                    host = "example.com",
                    uptimeCheckInterval = 60,
                )
                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(
                        HttpRequest.POST("/api/v2/dns-monitors/", createDto).header("X-Api-Key", "test"),
                        String::class.java,
                    )
                }

                then("it should return 409 CONFLICT and not create a duplicate") {
                    ex.status shouldBe HttpStatus.CONFLICT
                    monitorRepository.fetchAll().filter { it.name == existing.name } shouldHaveSize 1
                }
            }
        }

        given("PATCH /api/v2/dns-monitors/{id}") {
            `when`("it is updated to a name another monitor already has") {
                val first = createDnsMonitor(monitorRepository)
                val second = createDnsMonitor(monitorRepository)
                val updateNode = mapper.createObjectNode().put("name", first.name)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(
                        HttpRequest.PATCH("/api/v2/dns-monitors/${second.id}", updateNode)
                    ).awaitFirst()
                }

                then("it should return 409 CONFLICT") {
                    ex.status shouldBe HttpStatus.CONFLICT
                }
            }

            `when`("a disabled monitor is enabled") {
                val monitor = createDnsMonitor(monitorRepository, enabled = false)
                checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldBeNull()
                val updateNode = mapper.createObjectNode().put("enabled", true)

                monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should persist the change and schedule the checks") {
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().enabled shouldBe true
                    checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                }
            }

            `when`("a monitor is updated") {
                val monitor = createDnsMonitor(monitorRepository, host = "example.com")

                val newMatchers = listOf(
                    DnsRecordMatcher(DnsRecordType.MX, DnsMatchType.CONTAINS, "mail"),
                )
                val updateNode = mapper.createObjectNode()
                    .put("host", "other.example.com")
                    .put("resolverHost", "8.8.8.8")
                    .put("resolverPort", 5353)
                    .put("transport", DnsTransport.TCP.name)
                updateNode.replace("recordMatchers", mapper.valueToTree(newMatchers))
                updateNode.replace("driftRecordTypes", mapper.valueToTree(listOf(DnsRecordType.NS, DnsRecordType.MX)))
                val updatedMonitor = monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should update the monitor, including the DNS-specific JSONB, array and enum fields") {
                    updatedMonitor.host shouldBe "other.example.com"
                    val persisted = monitorRepository.findById(monitor.id, null).shouldNotBeNull()
                    persisted.resolverHost shouldBe "8.8.8.8"
                    persisted.resolverPort shouldBe 5353
                    persisted.transport shouldBe DnsTransport.TCP
                    persisted.driftRecordTypes.toList() shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
                    persisted.recordMatchersAsList() shouldBe newMatchers
                }

                then("it should reschedule checks") {
                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe true
                }
            }

            `when`("a monitor is updated with duplicated matchers and drift record types") {
                val monitor = createDnsMonitor(monitorRepository, host = "example.com")

                val matcher = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")
                val updateNode = mapper.createObjectNode()
                updateNode.replace("recordMatchers", mapper.valueToTree(listOf(matcher, matcher)))
                updateNode.replace(
                    "driftRecordTypes",
                    mapper.valueToTree(listOf(DnsRecordType.NS, DnsRecordType.NS, DnsRecordType.MX)),
                )
                monitorClient.updateMonitor(monitor.id, updateNode)

                then("the duplicates are collapsed, the same way they are on the create path") {
                    val persisted = monitorRepository.findById(monitor.id, null).shouldNotBeNull()
                    persisted.recordMatchersAsList() shouldBe listOf(matcher)
                    persisted.driftRecordTypes.toList() shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
                }
            }

            `when`("the expected response code is updated") {
                val monitor = createDnsMonitor(monitorRepository, host = "does-not-exist.example.com")
                monitor.expectedResponseCode shouldBe DnsResponseCode.NOERROR

                val updateNode = mapper.createObjectNode().put("expectedResponseCode", DnsResponseCode.NXDOMAIN.name)
                monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should persist the new expected response code") {
                    monitorRepository.findById(monitor.id, null)
                        .shouldNotBeNull().expectedResponseCode shouldBe DnsResponseCode.NXDOMAIN
                }
            }

            `when`("metricsHistoryEnabled is updated to false and there are existing metrics logs") {
                val monitor = createDnsMonitor(monitorRepository, metricsHistoryEnabled = true)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 200)
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
                        HttpRequest.PATCH("/api/v2/dns-monitors/999999", updateNode)
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called to update a monitor's name that is also present on a status page - writable") {
                val monitor1 = createDnsMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createDnsMonitor(monitorRepository, monitorName = "monitor2")
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.DNS, monitor1.name),
                        MonitorID(MonitorType.DNS, monitor2.name),
                    )
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor2.name))
                )

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DnsMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()
                val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()

                then("it should update the monitor and also update the monitor reference on the status pages") {
                    monitorInDb.name shouldBe "updated_monitor1"

                    statusPage1InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DNS, "updated_monitor1"),
                        MonitorID(MonitorType.DNS, monitor2.name),
                    )
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DNS, monitor2.name),
                    )
                    statusPage2InDb.updatedAt shouldBe statusPage2InDb.createdAt
                }
            }

            `when`("it is called to update a monitor's name that is NOT present on a non-writable status page") {
                val monitor1 = createDnsMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createDnsMonitor(monitorRepository, monitorName = "monitor2")
                createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor2.name))
                )

                appConfig.disableStatusPageExternalWrite()

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DnsMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()

                then("it should update the monitor") {
                    monitorInDb.name shouldBe "updated_monitor1"
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called to update a monitor's name that is present on a non-writable status page") {
                val monitor = createDnsMonitor(monitorRepository, monitorName = "monitor1")
                createStatusPage(
                    dslContext,
                    public = false,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor.name))
                )
                appConfig.disableStatusPageExternalWrite()
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DnsMonitorUpdateDto::name.name, "updated_monitor1")

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

        given("DELETE /api/v2/dns-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createDnsMonitor(monitorRepository)

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
                        HttpRequest.DELETE<String>("/api/v2/dns-monitors/999999")
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called with an existing monitor that belongs to more than one status page") {
                val monitor = createDnsMonitor(monitorRepository, monitorName = "test_monitor")
                val anotherMonitor = createDnsMonitor(monitorRepository, monitorName = "another_test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/dns-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor.name))
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.DNS, monitor.name),
                        MonitorID(MonitorType.DNS, anotherMonitor.name),
                    )
                )
                val statusPageWithoutDeletedMonitor = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DNS, anotherMonitor.name))
                )
                delay(1000.milliseconds)

                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(monitor.id, null)

                then("it should delete the monitor from the status pages and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe false

                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.DNS, monitor.id)

                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors.shouldBeEmpty()
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()
                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DNS, anotherMonitor.name)
                    )
                    statusPage2InDb.updatedAt shouldBeAfter statusPage2InDb.createdAt

                    val statusPage3InDb = statusPageRepository.findById(statusPageWithoutDeletedMonitor.id)
                        .shouldNotBeNull()
                    statusPage3InDb.monitors.shouldContainExactly(statusPageWithoutDeletedMonitor.monitors)
                    statusPage3InDb.updatedAt shouldBe statusPage3InDb.createdAt
                }
            }

            `when`("it is called with an existing monitor that belongs to a non-writable status page") {
                val monitor = createDnsMonitor(monitorRepository, monitorName = "test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/dns-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor.name))
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

        given("GET /api/v2/dns-monitors/{id}/uptime-events") {
            `when`("there are events for the monitor") {
                val monitor = createDnsMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createDnsUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusSeconds(60),
                    status = UptimeStatus.DOWN,
                    endedAt = now,
                )
                createDnsUptimeEventRecord(
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

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/dns-monitors/1232132432/uptime-events").awaitFirst()
                }

                then("it should return 404 NOT_FOUND") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("GET /api/v2/dns-monitors/{id}/stats") {
            `when`("metrics log records are present") {
                val monitor = createDnsMonitor(monitorRepository)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 200)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 150)

                then("it should return the correct latency stats and latency logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalDnsUptimeStats(
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
                    stats.metricsLogs shouldHaveSize 3
                    // Latency logs should be sorted by their creation in descending order
                    stats.metricsLogs[0].id shouldBeGreaterThan stats.metricsLogs[1].id
                }
            }

            `when`("metrics log records outside the period are excluded") {
                val monitor = createDnsMonitor(monitorRepository)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 300)
                // This record is outside the 4-minute period
                createDnsMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 600,
                    createdAt = getCurrentTimestamp().minusMinutes(5),
                )

                then("only records within the period should be considered for latency stats") {
                    val testPeriod = Duration.ofMinutes(4)
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalDnsUptimeStats(
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
                    stats.uptimeHistory.period shouldBe "PT4M"
                }
            }

            `when`("there are no metrics log records") {
                val monitor = createDnsMonitor(monitorRepository)

                then("it should return null for latency stats and an empty list for logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalDnsUptimeStats(
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
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("metricsHistoryEnabled is false") {
                val monitor = createDnsMonitor(monitorRepository, metricsHistoryEnabled = false)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)

                then("it should return stats with metricsHistoryEnabled=false and no latency data") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalDnsUptimeStats(
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
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/dns-monitors/999999/stats").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("GET /api/v2/dns-monitors/stats") {

            val monitoringStatsDtoStub = DnsMonitoringStatsDto(
                actual = DnsMonitoringStatsDto.ActualMonitoringStats(
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
                history = DnsMonitoringStatsDto.HistoricalMonitoringStats(
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
                every { statCalculatorMock.calculateOverallDnsStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = null)

                then("it should delegate to the StatCalculator with the default period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallDnsStats(Duration.ofHours(168)) }
                }
            }

            `when`("it's called with an explicit period") {

                val statCalculatorMock = getMock(statCalculator)
                every { statCalculatorMock.calculateOverallDnsStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = Duration.ofDays(1))

                then("it should delegate to the StatCalculator with the explicit period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallDnsStats(Duration.ofDays(1)) }
                }
            }
        }
    }

    @MockBean(StatCalculator::class)
    fun mockStatCalculator() = mockk<StatCalculator>()
}
