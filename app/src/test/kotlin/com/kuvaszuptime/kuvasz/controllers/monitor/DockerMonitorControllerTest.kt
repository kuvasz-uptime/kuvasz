package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.createDockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.randomClientSecret
import com.kuvaszuptime.kuvasz.models.ApiErrorCode
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.docker.DockerCheckScheduler
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerState
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.util.getBodyAs
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
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
import java.math.BigDecimal
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest(environments = ["full-integrations-setup", "docker-hosts"])
class DockerMonitorControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: DockerMonitorClient,
    private val monitorRepository: DockerMonitorRepository,
    private val metricsLogRepository: DockerMetricsLogRepository,
    private val checkScheduler: DockerCheckScheduler,
    private val statCalculator: StatCalculator,
    private val eventDispatcher: EventDispatcher,
    private val statusPageRepository: StatusPageRepository,
    private val appConfig: AppConfig,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk {
        every { inspectContainer(any(), any(), any()) } returns DockerInspectResult.Inspected(
            state = DockerContainerState(
                status = DockerContainerStatus.RUNNING,
                health = DockerHealthStatus.NONE,
                exitCode = null,
                oomKilled = false,
                failingStreak = null,
            ),
            latencyMs = 10,
        )
    }

    init {
        given("GET /api/v2/docker-monitors/") {
            `when`("there are monitors in the database") {
                val monitor = createDockerMonitor(monitorRepository, container = "my-app")
                val now = getCurrentTimestamp()
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null,
                )

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                    category = null,
                )

                then("it should return them with details") {
                    response shouldHaveSize 1
                    val item = response.first()
                    item.id shouldBe monitor.id
                    item.name shouldBe monitor.name
                    item.dockerHost shouldBe monitor.dockerHost
                    item.container shouldBe "my-app"
                    item.uptimeCheckInterval shouldBe monitor.uptimeCheckInterval
                    item.timeoutMs shouldBe monitor.timeoutMs
                    item.failureCountThreshold shouldBe monitor.failureCountThreshold
                    item.metricsHistoryEnabled shouldBe true
                    item.enabled shouldBe monitor.enabled
                    item.uptimeStatus shouldBe UptimeStatus.UP
                }
            }

            `when`("filtering by enabled=false") {
                createDockerMonitor(monitorRepository, enabled = true)
                createDockerMonitor(monitorRepository, enabled = false)

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = false,
                    uptimeStatus = null,
                    category = null,
                )

                then("only disabled monitors should be returned") {
                    response shouldHaveSize 1
                    response.first().enabled shouldBe false
                }
            }

            `when`("filtering by uptimeStatus=UP") {
                val upMonitor = createDockerMonitor(monitorRepository)
                val downMonitor = createDockerMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = upMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = downMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.DOWN,
                    endedAt = null
                )

                val response =
                    monitorClient.getMonitorsWithDetails(
                        enabled = null,
                        uptimeStatus = listOf(UptimeStatus.UP),
                        category = null,
                    )

                then("only UP monitors should be returned") {
                    response shouldHaveSize 1
                    response.first().id shouldBe upMonitor.id
                }
            }
            `when`("filtering by a category") {
                val payments = createDockerMonitor(monitorRepository, monitorName = "pays", category = "Payments")
                createDockerMonitor(monitorRepository, monitorName = "searches", category = "Search")
                createDockerMonitor(monitorRepository, monitorName = "plain", category = null)

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                    category = "Payments",
                )

                then("only the monitors of that category should be returned") {
                    response.map { it.id } shouldContainExactly listOf(payments.id)
                }
            }

            `when`("filtering by an empty category") {
                createDockerMonitor(monitorRepository, monitorName = "pays", category = "Payments")
                val plain = createDockerMonitor(monitorRepository, monitorName = "plain", category = null)

                // An empty value is the uncategorized selector, in contrast to omitting the parameter entirely
                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                    category = "",
                )

                then("only the monitors without a category should be returned") {
                    response.map { it.id } shouldContainExactly listOf(plain.id)
                }
            }

            `when`("combining the category filter with the other options") {
                val enabledPayments =
                    createDockerMonitor(monitorRepository, monitorName = "pays", category = "Payments")
                createDockerMonitor(monitorRepository, monitorName = "paused", category = "Payments", enabled = false)
                createDockerMonitor(monitorRepository, monitorName = "searches", category = "Search")

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = true,
                    uptimeStatus = null,
                    category = "Payments",
                )

                then("they should narrow the result together") {
                    response.map { it.id } shouldContainExactly listOf(enabledPayments.id)
                }
            }

            `when`("filtering by a category no monitor belongs to") {
                createDockerMonitor(monitorRepository, monitorName = "pays", category = "Payments")

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                    category = "Nobody uses me",
                )

                then("an empty list should be returned") {
                    response.shouldBeEmpty()
                }
            }
        }

        given("GET /api/v2/docker-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createDockerMonitor(monitorRepository)
                createMaintenanceWindow(
                    dslContext,
                    name = "active-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name)),
                )

                val response = monitorClient.getMonitorDetails(monitor.id)

                then("it should return monitor details") {
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.dockerHost shouldBe monitor.dockerHost
                    response.container shouldBe monitor.container
                    response.uptimeStatus.shouldBeNull()
                    response.maintenanceWindows.map { it.name } shouldBe listOf("active-window")
                    response.maintenanceWindows.single().active shouldBe true
                    response.inMaintenance shouldBe true
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/docker-monitors/999999").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("POST /api/v2/docker-monitors/") {
            `when`("a valid monitor is created") {
                val monitorName = randomClientSecret()
                val createDto = DockerMonitorCreateDto(
                    name = monitorName,
                    dockerHost = "local",
                    container = "my-app",
                    uptimeCheckInterval = 60,
                    timeoutMs = DockerMonitorDefaults.TIMEOUT_MS,
                    failureCountThreshold = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/docker-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should create the monitor and return 201") {
                    response.status shouldBe HttpStatus.CREATED
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    createdMonitor.dockerHost shouldBe "local"
                    createdMonitor.container shouldBe "my-app"
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
                val createDto = DockerMonitorCreateDto(
                    name = monitorName,
                    dockerHost = "local",
                    container = "my-app",
                    uptimeCheckInterval = 60,
                    timeoutMs = DockerMonitorDefaults.TIMEOUT_MS,
                    failureCountThreshold = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                    metricsHistoryEnabled = false,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/docker-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should create the monitor with metricsHistoryEnabled=false and return 201") {
                    response.status shouldBe HttpStatus.CREATED
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    createdMonitor.metricsHistoryEnabled shouldBe false
                }
            }

            `when`("ignoreConnectivityCheck is set to true when creating a monitor") {
                val monitorName = randomClientSecret()
                val createDto = DockerMonitorCreateDto(
                    name = monitorName,
                    dockerHost = "local",
                    container = "my-app",
                    uptimeCheckInterval = 60,
                    timeoutMs = DockerMonitorDefaults.TIMEOUT_MS,
                    failureCountThreshold = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD,
                    enabled = true,
                    integrations = null,
                    ignoreConnectivityCheck = true,
                )

                val response = client.toBlocking().exchange(
                    HttpRequest.POST("/api/v2/docker-monitors/", createDto).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should create the monitor with ignoreConnectivityCheck=true and return 201") {
                    response.status shouldBe HttpStatus.CREATED
                    val createdMonitor = monitorRepository.findByName(monitorName)
                    createdMonitor.shouldNotBeNull()
                    createdMonitor.ignoreConnectivityCheck shouldBe true
                }
            }

            `when`("ignoreConnectivityCheck is updated") {
                val monitor = createDockerMonitor(monitorRepository)
                // Unlike every other type, a Docker monitor starts out ignoring the connectivity check
                monitor.ignoreConnectivityCheck shouldBe true
                val updateNode = mapper.createObjectNode().put("ignoreConnectivityCheck", false)

                val response = client.toBlocking().exchange(
                    HttpRequest.PATCH("/api/v2/docker-monitors/${monitor.id}", updateNode).header("X-Api-Key", "test"),
                    String::class.java
                )

                then("it should persist the new value and return 200") {
                    response.status shouldBe HttpStatus.OK
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull()
                        .ignoreConnectivityCheck shouldBe false
                }

                then("the details projection should expose the new value too") {
                    monitorRepository.getMonitorWithDetails(monitor.id).shouldNotBeNull()
                        .ignoreConnectivityCheck shouldBe false
                }
            }

            `when`("validation fails - blank dockerHost") {
                val request = HttpRequest.POST(
                    "/api/v2/docker-monitors/",
                    mapOf(
                        "name" to "test",
                        "dockerHost" to " ",
                        "container" to "my-app",
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

            `when`("validation fails - timeoutMs out of range") {
                val request = HttpRequest.POST(
                    "/api/v2/docker-monitors/",
                    mapOf(
                        "name" to "test",
                        "dockerHost" to "local",
                        "container" to "my-app",
                        "uptimeCheckInterval" to 60,
                        "timeoutMs" to 30001,
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

            `when`("validation fails - container is blank") {
                val request = HttpRequest.POST(
                    "/api/v2/docker-monitors/",
                    mapOf(
                        "name" to "test",
                        "dockerHost" to "local",
                        "container" to "",
                        "uptimeCheckInterval" to 60,
                    )
                ).header("X-Api-Key", "test")

                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(request, String::class.java)
                }

                then("it should return 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("a monitor names a Docker host that is not configured") {
                val monitorName = randomClientSecret()
                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(
                        HttpRequest.POST(
                            "/api/v2/docker-monitors/",
                            DockerMonitorCreateDto(
                                name = monitorName,
                                dockerHost = "not-configured",
                                container = "my-app",
                                uptimeCheckInterval = 60,
                            ),
                        ).header("X-Api-Key", "test"),
                        String::class.java,
                    )
                }

                then("it should return 400 and not create the monitor") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<ServiceError>()?.message shouldBe
                        "Non-existing Docker host found: not-configured."
                    monitorRepository.findByName(monitorName).shouldBeNull()
                }
            }

            `when`("a monitor with an already-taken name is created") {
                val existing = createDockerMonitor(monitorRepository)
                val createDto = DockerMonitorCreateDto(
                    name = existing.name,
                    dockerHost = "local",
                    container = "my-app",
                    uptimeCheckInterval = 60,
                )
                val ex = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange(
                        HttpRequest.POST("/api/v2/docker-monitors/", createDto).header("X-Api-Key", "test"),
                        String::class.java,
                    )
                }

                then("it should return 409 CONFLICT and not create a duplicate") {
                    ex.status shouldBe HttpStatus.CONFLICT
                    monitorRepository.fetchAll().filter { it.name == existing.name } shouldHaveSize 1
                }
            }
        }

        given("PATCH /api/v2/docker-monitors/{id}") {
            `when`("it is updated to a name another monitor already has") {
                val first = createDockerMonitor(monitorRepository)
                val second = createDockerMonitor(monitorRepository)
                val updateNode = mapper.createObjectNode().put("name", first.name)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(
                        HttpRequest.PATCH("/api/v2/docker-monitors/${second.id}", updateNode)
                    ).awaitFirst()
                }

                then("it should return 409 CONFLICT") {
                    ex.status shouldBe HttpStatus.CONFLICT
                }
            }

            `when`("a disabled monitor is enabled") {
                val monitor = createDockerMonitor(monitorRepository, enabled = false)
                checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldBeNull()
                val updateNode = mapper.createObjectNode().put("enabled", true)

                monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should persist the change and schedule the checks") {
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().enabled shouldBe true
                    checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                }
            }

            `when`("a monitor is updated") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local", container = "my-app")

                val updateNode = mapper.createObjectNode()
                    .put("dockerHost", "vps-1")
                    .put("container", "other-app")
                    .put("category", "New category")
                val updatedMonitor = monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should update the monitor") {
                    updatedMonitor.dockerHost shouldBe "vps-1"
                    updatedMonitor.container shouldBe "other-app"
                    updatedMonitor.category shouldBe "New category"
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().container shouldBe "other-app"
                }

                then("it should reschedule checks") {
                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe true
                }
            }

            // The host is a plain name, not a foreign key: removing a host from the config must not make the
            // monitors referencing it un-editable, and their checks report the dangling reference instead
            `when`("a monitor whose Docker host was removed from the config is updated") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "removed-host")

                val updateNode = mapper.createObjectNode().put("container", "other-app")
                val updatedMonitor = monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should keep the host and apply the update") {
                    updatedMonitor.dockerHost shouldBe "removed-host"
                    updatedMonitor.container shouldBe "other-app"
                }
            }

            `when`("a monitor is pointed to a Docker host that is not configured") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")

                val updateNode = mapper.createObjectNode().put("dockerHost", "not-configured")
                val updatedMonitor = monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should accept it, since only the creation is validated against the configured hosts") {
                    updatedMonitor.dockerHost shouldBe "not-configured"
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().dockerHost shouldBe "not-configured"
                }
            }

            `when`("metricsHistoryEnabled is updated to false and there are existing metrics logs") {
                val monitor = createDockerMonitor(monitorRepository, metricsHistoryEnabled = true)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 200)
                metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldNotBeNull()

                val updateNode = mapper.createObjectNode().put("metricsHistoryEnabled", false)
                monitorClient.updateMonitor(monitor.id, updateNode)

                then("it should update the monitor and remove existing metrics log records") {
                    val monitorInDb = monitorRepository.findById(monitor.id, null).shouldNotBeNull()
                    monitorInDb.metricsHistoryEnabled shouldBe false
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("the monitor does not exist") {
                val updateNode = mapper.createObjectNode().put("container", "other-app")
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(
                        HttpRequest.PATCH("/api/v2/docker-monitors/999999", updateNode)
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called to update a monitor's name that is also present on a status page - writable") {
                val monitor1 = createDockerMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createDockerMonitor(monitorRepository, monitorName = "monitor2")
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.DOCKER, monitor1.name),
                        MonitorID(MonitorType.DOCKER, monitor2.name),
                    )
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor2.name))
                )

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DockerMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()
                val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()

                then("it should update the monitor and also update the monitor reference on the status pages") {
                    monitorInDb.name shouldBe "updated_monitor1"

                    statusPage1InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DOCKER, "updated_monitor1"),
                        MonitorID(MonitorType.DOCKER, monitor2.name),
                    )
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DOCKER, monitor2.name),
                    )
                    statusPage2InDb.updatedAt shouldBe statusPage2InDb.createdAt
                }
            }

            `when`("it is called to update a monitor's name that is NOT present on a non-writable status page") {
                val monitor1 = createDockerMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createDockerMonitor(monitorRepository, monitorName = "monitor2")
                createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor2.name))
                )

                appConfig.disableStatusPageExternalWrite()

                delay(1000.milliseconds)
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DockerMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()

                then("it should update the monitor") {
                    monitorInDb.name shouldBe "updated_monitor1"
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called to update a monitor's name that is present on a non-writable status page") {
                val monitor = createDockerMonitor(monitorRepository, monitorName = "monitor1")
                createStatusPage(
                    dslContext,
                    public = false,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name))
                )
                appConfig.disableStatusPageExternalWrite()
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(DockerMonitorUpdateDto::name.name, "updated_monitor1")

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

        given("DELETE /api/v2/docker-monitors/{id}") {
            `when`("the monitor exists") {
                val monitor = createDockerMonitor(monitorRepository)

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
                        HttpRequest.DELETE<String>("/api/v2/docker-monitors/999999")
                    ).awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called with an existing monitor that belongs to more than one status page") {
                val monitor = createDockerMonitor(monitorRepository, monitorName = "test_monitor")
                val anotherMonitor = createDockerMonitor(monitorRepository, monitorName = "another_test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/docker-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name))
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.DOCKER, monitor.name),
                        MonitorID(MonitorType.DOCKER, anotherMonitor.name),
                    )
                )
                val statusPageWithoutDeletedMonitor = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, anotherMonitor.name))
                )
                delay(1000.milliseconds)

                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(monitor.id, null)

                then("it should delete the monitor from the status pages and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    checkScheduler.getScheduledUptimeChecks().containsKey(monitor.id) shouldBe false

                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.DOCKER, monitor.id)

                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors.shouldBeEmpty()
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()
                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.DOCKER, anotherMonitor.name)
                    )
                    statusPage2InDb.updatedAt shouldBeAfter statusPage2InDb.createdAt

                    val statusPage3InDb = statusPageRepository.findById(statusPageWithoutDeletedMonitor.id)
                        .shouldNotBeNull()
                    statusPage3InDb.monitors.shouldContainExactly(statusPageWithoutDeletedMonitor.monitors)
                    statusPage3InDb.updatedAt shouldBe statusPage3InDb.createdAt
                }
            }

            `when`("it is called with an existing monitor that belongs to a non-writable status page") {
                val monitor = createDockerMonitor(monitorRepository, monitorName = "test_monitor")
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/docker-monitors/${monitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name))
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

        given("GET /api/v2/docker-monitors/{id}/uptime-events") {
            `when`("there are events for the monitor") {
                val monitor = createDockerMonitor(monitorRepository)
                val now = getCurrentTimestamp()
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusSeconds(60),
                    status = UptimeStatus.DOWN,
                    endedAt = now,
                )
                createDockerUptimeEventRecord(
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
                    client.exchange("/api/v2/docker-monitors/1232132432/uptime-events").awaitFirst()
                }

                then("it should return 404 NOT_FOUND") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("GET /api/v2/docker-monitors/{id}/stats") {
            `when`("metrics log records are present") {
                val monitor = createDockerMonitor(monitorRepository)
                listOf(
                    BigDecimal("10.00") to 100L,
                    BigDecimal("20.00") to 200L,
                    BigDecimal("30.00") to 300L,
                ).forEach { (cpu, memory) ->
                    createDockerMetricsLogRecord(
                        dslContext,
                        monitorId = monitor.id,
                        cpuUsagePercent = cpu,
                        memoryUsageBytes = memory,
                    )
                }

                then("it should return the correct resource stats and metrics logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalUptimeStats(
                            monitorType = MonitorType.DOCKER,
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

                    stats.cpuStats shouldNotBeNull {
                        averageCpuUsagePercentage.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("20.00")
                        minCpuUsagePercentage.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("10.00")
                        maxCpuUsagePercentage.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("30.00")
                    }
                    stats.memoryStats shouldNotBeNull {
                        averageMemoryUsageBytes shouldBe 200L
                        minMemoryUsageBytes shouldBe 100L
                        maxMemoryUsageBytes shouldBe 300L
                    }
                    stats.metricsLogs shouldHaveSize 3
                    // Metrics logs should be sorted by their creation in descending order
                    stats.metricsLogs[0].id shouldBeGreaterThan stats.metricsLogs[1].id
                }

                // The container's own counters are what a Docker monitor charts, so the log rows carry them
                then("the metrics logs should expose the container's resource sample") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalUptimeStats(any(), any(), any())
                    } returns HistoricalUptimeStatsDto(
                        period = Duration.ofDays(1).toString(),
                        incidents = 0,
                        affectedMonitors = 0,
                        uptimeRatio = 1.0,
                        totalDowntimeSeconds = 0,
                    )

                    val latest = monitorClient.getMonitorStats(monitor.id, period = null).metricsLogs.first()
                    latest.cpuUsagePercent.shouldNotBeNull() shouldBeEqualComparingTo BigDecimal("30.00")
                    latest.memoryUsageBytes shouldBe 300L
                    latest.memoryLimitBytes shouldBe 8_388_608L
                }
            }

            `when`("metrics log records outside the period are excluded") {
                val monitor = createDockerMonitor(monitorRepository)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 300)
                // This record is outside the 4-minute period
                createDockerMetricsLogRecord(
                    dslContext,
                    monitorId = monitor.id,
                    latencyMs = 600,
                    createdAt = getCurrentTimestamp().minusMinutes(5),
                )

                then("only records within the period should be considered for the resource stats") {
                    val testPeriod = Duration.ofMinutes(4)
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalUptimeStats(
                            monitorType = MonitorType.DOCKER,
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
                    stats.memoryStats shouldNotBeNull {
                        averageMemoryUsageBytes shouldBe 1_048_576
                        minMemoryUsageBytes shouldBe 1_048_576
                        maxMemoryUsageBytes shouldBe 1_048_576
                    }
                    stats.uptimeHistory.period shouldBe "PT4M"
                }
            }

            `when`("there are no metrics log records") {
                val monitor = createDockerMonitor(monitorRepository)

                then("it should return null for the resource stats and an empty list for logs") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalUptimeStats(
                            monitorType = MonitorType.DOCKER,
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
                    stats.cpuStats shouldBe null
                    stats.memoryStats shouldBe null
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("metricsHistoryEnabled is false") {
                val monitor = createDockerMonitor(monitorRepository, metricsHistoryEnabled = false)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 100)

                then("it should return stats with metricsHistoryEnabled=false and no resource data") {
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalUptimeStats(
                            monitorType = MonitorType.DOCKER,
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
                    stats.cpuStats shouldBe null
                    stats.memoryStats shouldBe null
                    stats.metricsLogs.shouldBeEmpty()
                }
            }

            `when`("the monitor does not exist") {
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/docker-monitors/999999/stats").awaitFirst()
                }

                then("it should return 404") {
                    ex.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("GET /api/v2/docker-monitors/stats") {

            val monitoringStatsDtoStub = DockerMonitoringStatsDto(
                actual = DockerMonitoringStatsDto.ActualMonitoringStats(
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
                history = DockerMonitoringStatsDto.HistoricalMonitoringStats(
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
                every { statCalculatorMock.calculateOverallDockerStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = null)

                then("it should delegate to the StatCalculator with the default period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallDockerStats(Duration.ofHours(168)) }
                }
            }

            `when`("it's called with an explicit period") {

                val statCalculatorMock = getMock(statCalculator)
                every { statCalculatorMock.calculateOverallDockerStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = Duration.ofDays(1))

                then("it should delegate to the StatCalculator with the explicit period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallDockerStats(Duration.ofDays(1)) }
                }
            }
        }
    
        given("the category of a Docker monitor") {
            fun createWithCategory(category: String?, monitorName: String = randomClientSecret()) =
                monitorClient.createMonitor(
                    DockerMonitorCreateDto(
                        name = monitorName,
                        dockerHost = "local",
                        container = "my-app",
                        uptimeCheckInterval = 60,
                        category = category,
                    )
                )

            `when`("it is too long on creation") {
                val ex = shouldThrow<HttpClientResponseException> { createWithCategory("a".repeat(101)) }

                then("it should return a 400 with the interpolated maximum in its message") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.message shouldContain "Monitor category must be at most 100 characters long"
                }
            }

            `when`("it is exactly as long as the maximum") {
                val created = createWithCategory("a".repeat(100))

                then("it should be accepted") {
                    created.category shouldBe "a".repeat(100)
                }
            }

            `when`("it is blank or padded on creation") {
                val blank = createWithCategory("   ")
                val padded = createWithCategory("  Core services  ")

                then("a blank one should be persisted as null and a padded one trimmed") {
                    monitorRepository.findById(blank.id, null).shouldNotBeNull().category.shouldBeNull()
                    monitorRepository.findById(padded.id, null).shouldNotBeNull().category shouldBe "Core services"
                }
            }

            `when`("it is too long on update") {
                val monitor = createDockerMonitor(monitorRepository, category = "Old category")
                val updateNode = mapper.createObjectNode().put("category", "a".repeat(101))
                val ex = shouldThrow<HttpClientResponseException> {
                    monitorClient.updateMonitor(monitor.id, updateNode)
                }

                then("it should return a 400 and leave the monitor untouched") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.message shouldContain "Monitor category must be at most 100 characters long"
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().category shouldBe "Old category"
                }
            }

            `when`("it is blank or padded on update") {
                val blanked = createDockerMonitor(monitorRepository, category = "Old category")
                val padded = createDockerMonitor(monitorRepository, category = "Old category")
                monitorClient.updateMonitor(blanked.id, mapper.createObjectNode().put("category", "   "))
                monitorClient.updateMonitor(padded.id, mapper.createObjectNode().put("category", "  Payments  "))

                then("a blank one should be cleared and a padded one trimmed") {
                    monitorRepository.findById(blanked.id, null).shouldNotBeNull().category.shouldBeNull()
                    monitorRepository.findById(padded.id, null).shouldNotBeNull().category shouldBe "Payments"
                }
            }

            `when`("it is explicitly set to null on update") {
                val monitor = createDockerMonitor(monitorRepository, category = "Old category")
                monitorClient.updateMonitor(monitor.id, mapper.createObjectNode().putNull("category"))

                then("it should be cleared") {
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().category.shouldBeNull()
                }
            }

            `when`("it is not part of the update at all") {
                val monitor = createDockerMonitor(monitorRepository, category = "Old category")
                monitorClient.updateMonitor(monitor.id, mapper.createObjectNode().put("enabled", false))

                then("it should be left untouched") {
                    monitorRepository.findById(monitor.id, null).shouldNotBeNull().category shouldBe "Old category"
                }
            }
        }
}

    @MockBean(StatCalculator::class)
    fun mockStatCalculator() = mockk<StatCalculator>()
}
