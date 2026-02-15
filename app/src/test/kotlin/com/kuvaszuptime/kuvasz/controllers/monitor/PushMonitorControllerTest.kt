package com.kuvaszuptime.kuvasz.controllers.monitor

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.randomClientSecret
import com.kuvaszuptime.kuvasz.models.ApiErrorCode
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getBodyAs
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.date.shouldBeAfter
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
import java.time.Duration

@MicronautTest(environments = ["full-integrations-setup"])
class PushMonitorControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: PushMonitorClient,
    private val monitorRepository: PushMonitorRepository,
    private val statCalculator: StatCalculator,
    private val eventDispatcher: EventDispatcher,
    private val statusPageRepository: StatusPageRepository,
    private val appConfig: AppConfig,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    init {
        given("the getMonitorsWithDetails() endpoint") {
            `when`("there is a monitor in the database") {
                val setUpIntegrations = listOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.DISCORD, "global"),
                    IntegrationID(IntegrationType.TELEGRAM, "global"),
                    IntegrationID(IntegrationType.PAGERDUTY, "test_implicitly_enabled"),
                )
                val now = getCurrentTimestamp()
                val monitor = createPushMonitor(
                    monitorRepository,
                    integrations = setUpIntegrations,
                    lastHeartbeat = now.minusSeconds(5),
                    clientSecret = randomClientSecret(),
                    heartbeatInterval = 10,
                    failureCountThreshold = 2,
                    gracePeriod = 8
                )
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                )
                then("it should return them") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe monitor.id
                    responseItem.name shouldBe monitor.name
                    responseItem.heartbeatInterval shouldBe monitor.heartbeatInterval
                    responseItem.gracePeriod shouldBe monitor.gracePeriod
                    responseItem.clientSecret shouldBe monitor.clientSecret
                    responseItem.failureCountThreshold shouldBe monitor.failureCountThreshold
                    responseItem.enabled shouldBe monitor.enabled
                    responseItem.uptimeStatus shouldBe UptimeStatus.UP
                    responseItem.uptimeStatusStartedAt shouldBe now
                    responseItem.uptimeError shouldBe null
                    responseItem.lastUptimeCheck shouldBe now
                    responseItem.createdAt shouldBe monitor.createdAt
                    responseItem.lastHeartbeat shouldBe monitor.lastHeartbeat
                    responseItem.nextExpectedHeartbeat shouldBe monitor.lastHeartbeat.plusSeconds(18)
                    responseItem.statusPages.shouldContainExactlyInAnyOrder(
                        statusPage1.slug,
                        statusPage2.slug,
                    )

                    // Integrations
                    responseItem.integrations shouldContainExactlyInAnyOrder setUpIntegrations
                    responseItem.effectiveIntegrations shouldContainExactlyInAnyOrder setOf(
                        IntegrationDetailsDto(
                            id = "slack:test_implicitly_enabled",
                            enabled = true,
                            name = "test_implicitly_enabled",
                            type = IntegrationType.SLACK,
                            global = false,
                        ),
                        IntegrationDetailsDto(
                            id = "email:disabled",
                            enabled = false,
                            name = "disabled",
                            type = IntegrationType.EMAIL,
                            global = false,
                        ),
                        IntegrationDetailsDto(
                            id = "discord:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.DISCORD,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "telegram:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.TELEGRAM,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "pagerduty:test_implicitly_enabled",
                            enabled = true,
                            name = "test_implicitly_enabled",
                            type = IntegrationType.PAGERDUTY,
                            global = false,
                        ),
                        // Implicit globals should be included too
                        IntegrationDetailsDto(
                            id = "email:Global-343",
                            enabled = true,
                            name = "Global-343",
                            type = IntegrationType.EMAIL,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "slack:Global2",
                            enabled = true,
                            name = "Global2",
                            type = IntegrationType.SLACK,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "pagerduty:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.PAGERDUTY,
                            global = true,
                        ),
                    )
                }
            }

            `when`("enabled parameter is set to true") {
                createPushMonitor(monitorRepository, enabled = false, monitorName = "name1")
                val enabledMonitor = createPushMonitor(monitorRepository, monitorName = "name2")
                val response = monitorClient.getMonitorsWithDetails(
                    enabled = true,
                    uptimeStatus = null,
                )

                then("it should not return disabled monitor") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe enabledMonitor.id
                    responseItem.name shouldBe enabledMonitor.name
                }
            }

            `when`("enabled parameter is set to false") {
                val disabledMonitor = createPushMonitor(monitorRepository, enabled = false, monitorName = "name1")
                createPushMonitor(monitorRepository, monitorName = "name2")

                val response = monitorClient.getMonitorsWithDetails(
                    enabled = false,
                    uptimeStatus = null,
                )

                then("it should return only the disabled monitors") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe disabledMonitor.id
                    responseItem.name shouldBe disabledMonitor.name
                }
            }

            `when`("result is filtered by the uptime status") {
                val upMonitor = createPushMonitor(monitorRepository, monitorName = "up_monitor")
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = upMonitor.id,
                    startedAt = getCurrentTimestamp(),
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                val downMonitor = createPushMonitor(monitorRepository, monitorName = "down_monitor")
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = downMonitor.id,
                    startedAt = getCurrentTimestamp(),
                    status = UptimeStatus.DOWN,
                    endedAt = null,
                    error = "some error"
                )

                val upResponse = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = listOf(UptimeStatus.UP),
                )
                val downResponse = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = listOf(UptimeStatus.DOWN),
                )

                then("it should return only the monitors with the specified uptime status") {
                    upResponse.single().id shouldBe upMonitor.id
                    with(downResponse.single()) {
                        id shouldBe downMonitor.id
                        uptimeError shouldBe "some error"
                    }
                }
            }

            `when`("there isn't any monitor in the database") {
                val response = monitorClient.getMonitorsWithDetails(
                    enabled = null,
                    uptimeStatus = null,
                )
                then("it should return an empty list") {
                    response shouldHaveSize 0
                }
            }
        }

        given("MonitorController's getMonitorDetails() endpoint") {
            `when`("there is a monitor with the given ID in the database") {
                val setUpIntegrations = listOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.DISCORD, "global"),
                    IntegrationID(IntegrationType.TELEGRAM, "global"),
                    IntegrationID(IntegrationType.PAGERDUTY, "test_implicitly_enabled"),
                )
                val now = getCurrentTimestamp()
                val monitor = createPushMonitor(
                    monitorRepository,
                    integrations = setUpIntegrations,
                    lastHeartbeat = now.minusSeconds(5),
                    clientSecret = randomClientSecret(),
                    heartbeatInterval = 10,
                    gracePeriod = 8,
                )
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )

                then("it should return it") {
                    val response = monitorClient.getMonitorDetails(monitorId = monitor.id)
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.enabled shouldBe monitor.enabled
                    response.uptimeStatus shouldBe UptimeStatus.UP
                    response.uptimeStatusStartedAt shouldBe now
                    response.failureCountThreshold shouldBe monitor.failureCountThreshold
                    response.createdAt shouldBe monitor.createdAt
                    response.lastUptimeCheck shouldBe now
                    response.statusPages.shouldContainExactlyInAnyOrder(
                        statusPage1.slug,
                        statusPage2.slug,
                    )
                    response.uptimeError shouldBe null
                    response.lastHeartbeat shouldBe monitor.lastHeartbeat
                    response.nextExpectedHeartbeat shouldBe monitor.lastHeartbeat.plusSeconds(18)

                    // Integrations
                    response.integrations shouldContainExactlyInAnyOrder setUpIntegrations
                    response.effectiveIntegrations shouldContainExactlyInAnyOrder setOf(
                        IntegrationDetailsDto(
                            id = "slack:test_implicitly_enabled",
                            enabled = true,
                            name = "test_implicitly_enabled",
                            type = IntegrationType.SLACK,
                            global = false,
                        ),
                        IntegrationDetailsDto(
                            id = "email:disabled",
                            enabled = false,
                            name = "disabled",
                            type = IntegrationType.EMAIL,
                            global = false,
                        ),
                        IntegrationDetailsDto(
                            id = "discord:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.DISCORD,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "telegram:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.TELEGRAM,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "pagerduty:test_implicitly_enabled",
                            enabled = true,
                            name = "test_implicitly_enabled",
                            type = IntegrationType.PAGERDUTY,
                            global = false,
                        ),
                        // Implicit globals should be included too
                        IntegrationDetailsDto(
                            id = "email:Global-343",
                            enabled = true,
                            name = "Global-343",
                            type = IntegrationType.EMAIL,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "slack:Global2",
                            enabled = true,
                            name = "Global2",
                            type = IntegrationType.SLACK,
                            global = true,
                        ),
                        IntegrationDetailsDto(
                            id = "pagerduty:global",
                            enabled = true,
                            name = "global",
                            type = IntegrationType.PAGERDUTY,
                            global = true,
                        ),
                    )
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/push-monitors/1232132432").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getMonitorStats() endpoint") {

            `when`("explicit limit is set") {
                val monitor = createPushMonitor(
                    monitorRepository,
                )

                then("it delegate the calculation to the statcalculator") {

                    val testPeriod = Duration.ofMinutes(4)
                    val statCalculatorMock = getMock(statCalculator)
                    every {
                        statCalculatorMock.calculateHistoricalPushUptimeStats(
                            period = testPeriod,
                            monitorId = monitor.id,
                        )
                    } returns HistoricalUptimeStatsDto(
                        period = testPeriod.toString(),
                        incidents = 23,
                        affectedMonitors = 1,
                        uptimeRatio = 0.9823,
                        totalDowntimeSeconds = 8442,
                    )

                    val response = monitorClient.getMonitorStats(monitorId = monitor.id, period = testPeriod)
                    response.id shouldBe monitor.id
                    response.uptimeHistory.period shouldBe "PT4M"
                    response.uptimeHistory.incidents shouldBe 23
                    response.uptimeHistory.affectedMonitors shouldBe 1
                    response.uptimeHistory.uptimeRatio shouldBe 0.9823
                    response.uptimeHistory.totalDowntimeSeconds shouldBe 8442
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/push-monitors/1232132432/stats").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's createMonitor() endpoint") {

            `when`("it is called with a valid DTO - default parameters") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 12,
                    gracePeriod = 10,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)

                then("it should create a monitor") {

                    val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.heartbeatInterval shouldBe createdMonitor.heartbeatInterval
                    monitorInDb.gracePeriod shouldBe createdMonitor.gracePeriod
                    monitorInDb.enabled shouldBe true
                    monitorInDb.enabled shouldBe createdMonitor.enabled
                    monitorInDb.failureCountThreshold shouldBe 1
                    monitorInDb.failureCountThreshold shouldBe createdMonitor.failureCountThreshold
                    monitorInDb.clientSecret shouldBe createdMonitor.clientSecret
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldBe createdMonitor.createdAt
                    monitorInDb.integrations.shouldNotBeNull().shouldBeEmpty()
                }
            }

            `when`("it is called with a valid DTO - explicit parameters") {
                val setUpIntegrations = listOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "global"),
                    IntegrationID(IntegrationType.PAGERDUTY, "test_implicitly_enabled"),
                )
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 12,
                    gracePeriod = 10,
                    failureCountThreshold = 3,
                    clientSecret = randomClientSecret(),
                    enabled = false,
                    integrations = setUpIntegrations.map { it.toString() },
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)

                then("it should create a monitor") {
                    val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()
                    monitorInDb.name shouldBe "test_monitor"
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.heartbeatInterval shouldBe 12
                    monitorInDb.heartbeatInterval shouldBe createdMonitor.heartbeatInterval
                    monitorInDb.gracePeriod shouldBe 10
                    monitorInDb.gracePeriod shouldBe createdMonitor.gracePeriod
                    monitorInDb.failureCountThreshold shouldBe 3
                    monitorInDb.failureCountThreshold shouldBe createdMonitor.failureCountThreshold
                    monitorInDb.enabled shouldBe false
                    monitorInDb.enabled shouldBe createdMonitor.enabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldBe createdMonitor.createdAt
                    monitorInDb.integrations.shouldNotBeNull() shouldContainExactlyInAnyOrder
                        setUpIntegrations.toTypedArray()
                }
            }

            `when`("there is already a monitor with the same name") {
                val firstMonitor = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 12,
                    gracePeriod = 10,
                    clientSecret = randomClientSecret(),
                )
                val secondMonitor = PushMonitorCreateDto(
                    name = firstMonitor.name,
                    heartbeatInterval = 13,
                    gracePeriod = 11,
                    clientSecret = randomClientSecret(),
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstMonitor)
                val secondRequest = HttpRequest.POST("/api/v2/push-monitors", secondMonitor)
                val secondResponse = shouldThrow<HttpClientResponseException> {
                    client.exchange(secondRequest).awaitFirst()
                }

                then("it should return a 409") {
                    secondResponse.status shouldBe HttpStatus.CONFLICT
                    val monitorInDb = monitorRepository.findByName(firstCreatedMonitor.name)
                    monitorInDb.shouldNotBeNull()
                    monitorInDb.id shouldBe firstCreatedMonitor.id
                    monitorInDb.heartbeatInterval shouldBe firstCreatedMonitor.heartbeatInterval
                    monitorInDb.gracePeriod shouldBe firstCreatedMonitor.gracePeriod
                }
            }

            `when`("there is already a monitor with the same client secret") {
                val firstMonitor = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 12,
                    gracePeriod = 10,
                    clientSecret = randomClientSecret(),
                )
                val secondMonitor = PushMonitorCreateDto(
                    name = "test_monitor2",
                    heartbeatInterval = 13,
                    gracePeriod = 11,
                    clientSecret = firstMonitor.clientSecret,
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstMonitor)
                val secondRequest = HttpRequest.POST("/api/v2/push-monitors", secondMonitor)
                val secondResponse = shouldThrow<HttpClientResponseException> {
                    client.exchange(secondRequest).awaitFirst()
                }

                then("it should return a 409") {
                    secondResponse.status shouldBe HttpStatus.CONFLICT
                    val monitorInDb = monitorRepository.findByName(firstCreatedMonitor.name)
                    monitorInDb.shouldNotBeNull()
                    monitorInDb.id shouldBe firstCreatedMonitor.id
                    monitorInDb.heartbeatInterval shouldBe firstCreatedMonitor.heartbeatInterval
                    monitorInDb.gracePeriod shouldBe firstCreatedMonitor.gracePeriod
                }
            }

            `when`("it is called with an invalid heartbeat interval") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 9,
                    gracePeriod = 10,
                    clientSecret = randomClientSecret(),
                )
                val request = HttpRequest.POST("/api/v2/push-monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Heartbeat interval must be at least 10 seconds"
                }
            }

            `when`("it is called with an invalid grace period") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = -1,
                    clientSecret = randomClientSecret(),
                )
                val request = HttpRequest.POST("/api/v2/push-monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Grace period must be greater than or equal to 0 seconds"
                }
            }

            `when`("it is called with an invalid client secret") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = "this is too short",
                )
                val request = HttpRequest.POST("/api/v2/push-monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Client secret must be at least 36 characters long"
                }
            }

            `when`("it is called with an invalid integration name") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    integrations = listOf("invalid-integration")
                )
                val request = HttpRequest.POST("/api/v2/push-monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Invalid integration ID format: invalid-integration. Expected format is 'type:name'"
                }
            }

            `when`("it is called with a non-existing integration") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    integrations = listOf("email:non-existing-integration")
                )
                val request = HttpRequest.POST("/api/v2/push-monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Non-existing integration ID found: email:non-existing-integration."
                }
            }
        }

        given("MonitorController's deleteMonitor() endpoint") {

            `when`("it is called with an existing monitor ID") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/push-monitors/${createdMonitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }

                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null)

                then("it should delete the monitor") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    // A delete event should be dispatched
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.PUSH, createdMonitor.id)
                }
            }

            `when`("it is called with an existing monitor that belongs to more than one status page") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)
                val anotherMonitor = monitorClient.createMonitor(
                    monitorToCreate.copy(name = "another_test_monitor", clientSecret = randomClientSecret())
                )
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/push-monitors/${createdMonitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, createdMonitor.name)
                    )
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, createdMonitor.name),
                        MonitorID(MonitorType.PUSH, anotherMonitor.name),
                    )
                )
                val statusPageWithoutDeletedMonitor = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, anotherMonitor.name),
                    )
                )
                delay(1000) // Make sure that the status page update time is after the creation time

                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null)

                then("it should delete the monitor from the status pages") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    // A delete event should be dispatched
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.PUSH, createdMonitor.id)

                    // The monitor should be removed from both of the status pages
                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors.shouldBeEmpty()
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()
                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.PUSH, anotherMonitor.name)
                    )
                    statusPage2InDb.updatedAt shouldBeAfter statusPage2InDb.createdAt

                    val statusPage3InDb = statusPageRepository.findById(statusPageWithoutDeletedMonitor.id)
                        .shouldNotBeNull()
                    statusPage3InDb.monitors.shouldContainExactly(statusPageWithoutDeletedMonitor.monitors)
                    statusPage3InDb.updatedAt shouldBe statusPage3InDb.createdAt // Not updated
                }
            }

            `when`("it is called with an existing monitor that belongs to a non-writable status page") {
                val monitorToCreate = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/push-monitors/${createdMonitor.id}")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, createdMonitor.name)
                    )
                )
                delay(1000) // Make sure that the status page update time might be after the creation time
                appConfig.disableStatusPageExternalWrite()

                val ex = shouldThrow<HttpClientResponseException> { client.exchange(deleteRequest).awaitFirst() }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null)

                then("it should reject the deletion and return a 400") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<ServiceError>()?.errorCode shouldBe ApiErrorCode.MONITOR_CANNOT_BE_DELETED
                    monitorInDb.shouldNotBeNull()

                    // A delete event should not be dispatched
                    subscriber.assertNoValues()

                    // The monitor should be removed from both of the status pages
                    val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                    statusPage1InDb.monitors shouldBe statusPage1.monitors
                    statusPage1InDb.updatedAt shouldBe statusPage1InDb.createdAt
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/push-monitors/123232")
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }

                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(deleteRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND

                    // No delete event should be dispatched
                    subscriber.assertNoValues()
                }
            }
        }

        given("MonitorController's updateMonitor() endpoint") {

            `when`("it is called with an existing monitor ID and a valid DTO to update all of the values") {
                val setUpIntegrations = listOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "global"),
                    IntegrationID(IntegrationType.PAGERDUTY, "test_implicitly_enabled"),
                )
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    failureCountThreshold = 2,
                    clientSecret = randomClientSecret(),
                    integrations = setUpIntegrations.map { it.toString() },
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val newClientSecret = randomClientSecret()
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::enabled.name, false)
                    .put(PushMonitorUpdateDto::name.name, "updated_test_monitor")
                    .put(PushMonitorUpdateDto::heartbeatInterval.name, "5000")
                    .put(PushMonitorUpdateDto::gracePeriod.name, "20")
                    .put(PushMonitorUpdateDto::clientSecret.name, newClientSecret)
                    .put(PushMonitorUpdateDto::failureCountThreshold.name, 3)
                    .set<ObjectNode>(
                        PushMonitorUpdateDto::integrations.name,
                        mapper
                            .createArrayNode()
                            .add("slack:test_implicitly_enabled")
                            .add("telegram:disabled")
                    )

                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }

                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should update the monitor") {
                    monitorInDb.name shouldBe "updated_test_monitor"
                    monitorInDb.heartbeatInterval shouldBe 5000
                    monitorInDb.gracePeriod shouldBe 20
                    monitorInDb.enabled shouldBe false
                    monitorInDb.failureCountThreshold shouldBe 3
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldBeAfter monitorInDb.createdAt
                    monitorInDb.clientSecret shouldBe newClientSecret
                    monitorInDb.integrations.shouldNotBeNull() shouldContainExactlyInAnyOrder
                        arrayOf(
                            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                            IntegrationID(IntegrationType.TELEGRAM, "disabled"),
                        )

                    // An update event should be dispatched
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor shouldBe NumericMonitorID(MonitorType.PUSH, createdMonitor.id)
                }
            }

            `when`("it is called with an existing monitor ID and a valid DTO to enable the monitor") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    enabled = false,
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::enabled.name, true)
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should update the monitor and update only the present prop") {
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.heartbeatInterval shouldBe createdMonitor.heartbeatInterval
                    monitorInDb.enabled shouldBe true
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldBeAfter createdMonitor.updatedAt
                }
            }

            `when`("it is called to remove all the set up integrations") {
                val setUpIntegrations = listOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "global"),
                    IntegrationID(IntegrationType.PAGERDUTY, "test_implicitly_enabled"),
                )
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    integrations = setUpIntegrations.map { it.toString() },
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(PushMonitorUpdateDto::integrations.name, mapper.createArrayNode())
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should remove all the integrations") {
                    monitorInDb.integrations.shouldNotBeNull().shouldBeEmpty()
                }
            }

            `when`("integrations are omitted") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    integrations = listOf(
                        IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled").toString(),
                    ),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, "updated_test_monitor")
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should not change the integrations") {
                    monitorInDb.name shouldBe "updated_test_monitor"
                    monitorInDb.integrations.shouldNotBeNull() shouldContainExactlyInAnyOrder
                        arrayOf(IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"))
                }
            }

            `when`("it is called with an existing monitor ID but there is an other monitor with the given name") {
                val firstCreateDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstCreateDto)
                val secondCreateDto = PushMonitorCreateDto(
                    name = "test_monitor2",
                    heartbeatInterval = 11,
                    gracePeriod = 2,
                    clientSecret = randomClientSecret(),
                )
                val secondCreatedMonitor = monitorClient.createMonitor(secondCreateDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, secondCreatedMonitor.name)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${firstCreatedMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(firstCreatedMonitor.id, null).shouldNotBeNull()

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    monitorInDb.name shouldBe firstCreatedMonitor.name
                }
            }

            `when`("it is called for an existing monitor but there is another monitor with the given client secret") {
                val firstCreateDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstCreateDto)
                val secondCreateDto = PushMonitorCreateDto(
                    name = "test_monitor2",
                    heartbeatInterval = 11,
                    gracePeriod = 2,
                    clientSecret = randomClientSecret(),
                )
                val secondCreatedMonitor = monitorClient.createMonitor(secondCreateDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::clientSecret.name, secondCreatedMonitor.clientSecret)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${firstCreatedMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(firstCreatedMonitor.id, null).shouldNotBeNull()

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    monitorInDb.name shouldBe firstCreatedMonitor.name
                }
            }

            `when`("it is called to update a monitor's name that is also present on a status page - writable") {

                val monitor1 = createPushMonitor(
                    monitorRepository,
                    monitorName = "monitor1",
                )
                val monitor2 = createPushMonitor(
                    monitorRepository,
                    monitorName = "monitor2",
                )
                val statusPage1 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, monitor1.name),
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                )

                delay(1000) // Make sure that the status page update time is after the creation time
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()
                val statusPage1InDb = statusPageRepository.findById(statusPage1.id).shouldNotBeNull()
                val statusPage2InDb = statusPageRepository.findById(statusPage2.id).shouldNotBeNull()

                then("it should update the monitor and also update the monitor reference on the status pages") {
                    monitorInDb.name shouldBe "updated_monitor1"

                    statusPage1InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.PUSH, "updated_monitor1"),
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                    statusPage1InDb.updatedAt shouldBeAfter statusPage1InDb.createdAt

                    statusPage2InDb.monitors.shouldContainExactly(
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                    statusPage2InDb.updatedAt shouldBe statusPage2InDb.createdAt // Not updated
                }
            }

            `when`("it is called to update a monitor's name that is NOT present on a non-writable status page") {

                val monitor1 = createPushMonitor(
                    monitorRepository,
                    monitorName = "monitor1",
                )
                val monitor2 = createPushMonitor(
                    monitorRepository,
                    monitorName = "monitor2",
                )
                createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                )

                appConfig.disableStatusPageExternalWrite()

                delay(1000) // Make sure that the status page update time is after the creation time
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, "updated_monitor1")

                monitorClient.updateMonitor(monitor1.id, updateDto)
                val monitorInDb = monitorRepository.findById(monitor1.id, null).shouldNotBeNull()

                then("it should update the monitor") {
                    monitorInDb.name shouldBe "updated_monitor1"
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called to update a monitor's name that is present on a non-writable a status page") {

                val createdMonitor = createPushMonitor(
                    monitorRepository,
                    monitorName = "monitor1",
                )
                createStatusPage(
                    dslContext,
                    public = false,
                    monitors = listOf(
                        MonitorID(MonitorType.PUSH, createdMonitor.name),
                    )
                )
                appConfig.disableStatusPageExternalWrite()
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, "updated_monitor1")

                val ex = shouldThrow<HttpClientResponseException> {
                    monitorClient.updateMonitor(createdMonitor.id, updateDto)
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should not let the update happen and return a 400") {

                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    with(ex.response.getBodyAs<ServiceError>().shouldNotBeNull()) {
                        message shouldContain "The monitor's name cannot be changed, because it's already " +
                            "referenced in the YAML file by a status page."
                        errorCode shouldBe ApiErrorCode.MONITOR_NAME_CANNOT_BE_CHANGED
                    }
                    monitorInDb.name shouldBe createdMonitor.name
                    appConfig.enableStatusPageExternalWrite()
                }
            }

            `when`("it is called with a blank name") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::name.name, "\n")
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: name: Monitor name must not be blank"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with an empty client secret") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::clientSecret.name, "".repeat(36))
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: clientSecret: Client secret must not be blank"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a blank client secret") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::clientSecret.name, " ".repeat(36))
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: clientSecret: Client secret must not be blank"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a short client secret") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::clientSecret.name, "a".repeat(35))
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: clientSecret: Client secret must be at least 36 characters long"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a null on a property that is non-nullable") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .putNull(PushMonitorUpdateDto::enabled.name)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }

                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain "Validation failed: enabled: must not be null"
                    monitorInDb.name shouldBe createdMonitor.name

                    // No update event should be dispatched
                    subscriber.assertNoValues()
                }
            }

            `when`("it is called with a too short heartbeat interval") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::heartbeatInterval.name, 9)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: heartbeatInterval: Heartbeat interval must be at least 10 seconds"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a too low failure threshold") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                    failureCountThreshold = 2,
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::failureCountThreshold.name, 0)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: failureCountThreshold"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a non-existing monitor ID") {
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::enabled.name, false)
                val updateRequest = HttpRequest.PATCH("/api/v2/push-monitors/123232", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called with an invalid integration name") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(
                        PushMonitorUpdateDto::integrations.name,
                        mapper.createArrayNode().add("invalid-integration")
                    )
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Invalid JSON"
                    monitorInDb.integrations shouldContainExactlyInAnyOrder createdMonitor.integrations.toTypedArray()
                }
            }

            `when`("it is called with a non-existing integration") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(
                        PushMonitorUpdateDto::integrations.name,
                        mapper.createArrayNode().add("email:non-existing-integration")
                    )
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Non-existing integration ID found: email:non-existing-integration."
                    monitorInDb.integrations shouldContainExactlyInAnyOrder createdMonitor.integrations.toTypedArray()
                }
            }
            `when`("it is called to update a non-updatable field") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put("createdAt", "2023-01-01T00:00:00Z")
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val response = client.exchange(updateRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should not update the field and return a 200") {
                    response.status shouldBe HttpStatus.OK
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                }
            }

            `when`("it is called with a negative grace period") {
                val createDto = PushMonitorCreateDto(
                    name = "test_monitor",
                    heartbeatInterval = 10,
                    gracePeriod = 0,
                    clientSecret = randomClientSecret(),
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(PushMonitorUpdateDto::gracePeriod.name, -1)
                val updateRequest =
                    HttpRequest.PATCH("/api/v2/push-monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id, null).shouldNotBeNull()

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        MonitorValidationMessages.GRACE_PERIOD_POSITIVE_OR_ZERO
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }
        }

        given("MonitorController's getUptimeEvents() endpoint") {
            `when`("there is a monitor with the given ID in the database with uptime events") {
                val monitor = createPushMonitor(monitorRepository)
                val anotherMonitor =
                    createPushMonitor(monitorRepository, monitorName = "another_monitor")
                val now = getCurrentTimestamp()
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusDays(1),
                    status = UptimeStatus.DOWN,
                    endedAt = now
                )
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = anotherMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )

                then("it should return its uptime events") {
                    val response = monitorClient.getUptimeEvents(monitorId = monitor.id)
                    response shouldHaveSize 2
                    response.forAll { it.id shouldBeGreaterThan 0 }
                    response.forOne { it.status shouldBe UptimeStatus.UP }
                    response.forOne { it.status shouldBe UptimeStatus.DOWN }
                }
            }

            `when`("there is a monitor with the given ID in the database without uptime events") {
                val monitor = createPushMonitor(monitorRepository)

                then("it should return an empty list") {
                    val response = monitorClient.getUptimeEvents(monitorId = monitor.id)
                    response shouldHaveSize 0
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/push-monitors/1232132432/uptime-events").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the getMonitoringStats() endpoint") {

            val monitoringStatsDtoStub = PushMonitoringStatsDto(
                actual = PushMonitoringStatsDto.ActualMonitoringStats(
                    uptimeStats = PushMonitoringStatsDto.ActualMonitoringStats.ActualUptimeStats(
                        total = 10000,
                        down = 8185,
                        up = 3535,
                        paused = 7157,
                        inProgress = 6139,
                        lastIncident = getCurrentTimestamp()
                    ),
                ),
                history = PushMonitoringStatsDto.HistoricalMonitoringStats(
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
                every { statCalculatorMock.calculateOverallPushStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = null)

                then("it should delegate to the StatCalculator with the default period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallPushStats(Duration.ofHours(168)) }
                }
            }

            `when`("it's called with an explicit period") {

                val statCalculatorMock = getMock(statCalculator)
                every { statCalculatorMock.calculateOverallPushStats(any()) } returns monitoringStatsDtoStub

                val response = monitorClient.getMonitoringStats(period = Duration.ofDays(1))

                then("it should delegate to the StatCalculator with the default period and return the stats") {
                    response.shouldNotBeNull()

                    verify(exactly = 1) { statCalculatorMock.calculateOverallPushStats(Duration.ofDays(1)) }
                }
            }
        }
    }

    @MockBean(StatCalculator::class)
    fun mockStatCalculator() = mockk<StatCalculator>()
}
