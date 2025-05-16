package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.CheckScheduler
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getBodyAs
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest
class MonitorControllerTest(
    @Client("/") private val client: HttpClient,
    private val monitorClient: MonitorClient,
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val checkScheduler: CheckScheduler,
) : DatabaseBehaviorSpec() {

    init {
        given("MonitorController's getMonitorsWithDetails() endpoint") {
            `when`("there is a monitor in the database") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    endedAt = null
                )

                val response = monitorClient.getMonitorsWithDetails(enabledOnly = null)
                then("it should return them") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe monitor.id
                    responseItem.name shouldBe monitor.name
                    responseItem.url.toString() shouldBe monitor.url
                    responseItem.enabled shouldBe monitor.enabled
                    responseItem.enabled shouldBe monitor.sslCheckEnabled
                    responseItem.uptimeStatus shouldBe UptimeStatus.UP
                    responseItem.uptimeStatusStartedAt shouldBe now
                    responseItem.uptimeError shouldBe null
                    responseItem.lastUptimeCheck shouldBe now
                    responseItem.createdAt shouldBe monitor.createdAt
                    responseItem.sslStatus shouldBe SslStatus.VALID
                    responseItem.sslStatusStartedAt shouldBe now
                    responseItem.lastSSLCheck shouldBe now
                    responseItem.sslError shouldBe null
                    responseItem.pagerdutyKeyPresent shouldBe true
                    responseItem.requestMethod shouldBe HttpMethod.GET
                    responseItem.latencyHistoryEnabled shouldBe true
                    responseItem.forceNoCache shouldBe true
                    responseItem.followRedirects shouldBe true
                    responseItem.sslExpiryThreshold shouldBe monitor.sslExpiryThreshold
                    responseItem.sslValidUntil shouldBe null
                }
            }

            `when`("enabledOnly parameter is set to true") {
                createMonitor(monitorRepository, enabled = false, monitorName = "name1")
                val enabledMonitor = createMonitor(monitorRepository, monitorName = "name2")
                val response = monitorClient.getMonitorsWithDetails(enabledOnly = true)

                then("it should not return disabled monitor") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe enabledMonitor.id
                    responseItem.name shouldBe enabledMonitor.name
                    responseItem.url.toString() shouldBe enabledMonitor.url
                    responseItem.enabled shouldBe enabledMonitor.enabled
                    responseItem.sslCheckEnabled shouldBe enabledMonitor.sslCheckEnabled
                    responseItem.uptimeStatus shouldBe null
                    responseItem.sslStatus shouldBe null
                    responseItem.createdAt shouldBe enabledMonitor.createdAt
                    responseItem.pagerdutyKeyPresent shouldBe false
                    responseItem.requestMethod shouldBe HttpMethod.GET
                    responseItem.latencyHistoryEnabled shouldBe true
                    responseItem.forceNoCache shouldBe true
                    responseItem.followRedirects shouldBe true
                    responseItem.sslExpiryThreshold shouldBe enabledMonitor.sslExpiryThreshold
                    responseItem.sslValidUntil shouldBe null
                }
            }

            `when`("there isn't any monitor in the database") {
                val response = monitorClient.getMonitorsWithDetails(enabledOnly = false)
                then("it should return an empty list") {
                    response shouldHaveSize 0
                }
            }
        }

        given("MonitorController's getMonitorDetails() endpoint") {
            `when`("there is a monitor with the given ID in the database") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = true,
                    forceNoCache = false,
                    followRedirects = false,
                    sslExpiryThreshold = 15,
                )
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                val sslExpiryDate = getCurrentTimestamp().plusDays(60)
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    endedAt = null,
                    sslExpiryDate = sslExpiryDate,
                )

                then("it should return it") {
                    val response = monitorClient.getMonitorDetails(monitorId = monitor.id)
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.url.toString() shouldBe monitor.url
                    response.enabled shouldBe monitor.enabled
                    response.sslCheckEnabled shouldBe monitor.sslCheckEnabled
                    response.uptimeStatus shouldBe UptimeStatus.UP
                    response.createdAt shouldBe monitor.createdAt
                    response.lastUptimeCheck shouldBe now
                    response.sslStatus shouldBe SslStatus.VALID
                    response.sslStatusStartedAt shouldBe now
                    response.lastSSLCheck shouldBe now
                    response.sslError shouldBe null
                    response.pagerdutyKeyPresent shouldBe true
                    response.requestMethod shouldBe HttpMethod.HEAD
                    response.latencyHistoryEnabled shouldBe true
                    response.forceNoCache shouldBe false
                    response.followRedirects shouldBe false
                    response.sslExpiryThreshold shouldBe 15
                    response.sslValidUntil shouldBe sslExpiryDate
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v1/monitors/1232132432").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getMonitorStats() endpoint") {

            `when`("latency history enabled, latency records are present") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = true,
                    forceNoCache = false,
                    followRedirects = false,
                )
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 1200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)

                then("it should return the correct stats") {
                    val response = monitorClient.getMonitorStats(monitorId = monitor.id, latencyLogLimit = null)
                    response.id shouldBe monitor.id
                    response.latencyHistoryEnabled shouldBe true
                    response.averageLatencyInMs shouldBe 800
                    response.p95LatencyInMs shouldBe 1140
                    response.p99LatencyInMs shouldBe 1188
                    response.latencyLogs.shouldNotBeEmpty()
                    // Latency logs should be sorted by their creation in descending order
                    response.latencyLogs[0].id shouldBeGreaterThan response.latencyLogs[1].id
                    response.latencyLogs[1].id shouldBeGreaterThan response.latencyLogs[2].id
                }
            }

            `when`("latency history enabled, records are present, explicit limit is set") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = true,
                    forceNoCache = false,
                    followRedirects = false,
                )
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 100)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 500)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 400)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 300)

                then("it should limit the number of logs") {
                    val response = monitorClient.getMonitorStats(monitorId = monitor.id, latencyLogLimit = 3)
                    response.id shouldBe monitor.id
                    response.latencyHistoryEnabled shouldBe true
                    response.averageLatencyInMs shouldBe 300
                    response.p95LatencyInMs shouldBe 480
                    response.p99LatencyInMs shouldBe 496

                    response.latencyLogs shouldHaveSize 3
                    response.latencyLogs[0].latencyInMs shouldBe 300
                    response.latencyLogs[1].latencyInMs shouldBe 400
                    response.latencyLogs[2].latencyInMs shouldBe 500
                }
            }

            `when`("latency history enabled, but no records") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = true,
                    forceNoCache = false,
                    followRedirects = false,
                )

                then("it should return null for the latency stats and an empty list for the logs") {
                    val response = monitorClient.getMonitorStats(monitorId = monitor.id, latencyLogLimit = null)
                    response.id shouldBe monitor.id
                    response.latencyHistoryEnabled shouldBe true
                    response.averageLatencyInMs shouldBe null
                    response.p95LatencyInMs shouldBe null
                    response.p99LatencyInMs shouldBe null
                    response.latencyLogs.shouldBeEmpty()
                }
            }

            `when`("latency history disabled") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = false,
                    forceNoCache = false,
                    followRedirects = false,
                )
                // This situation is quite unlikely, because we don't store latency log records if history for them
                // is disabled, but it's better to test if they are explicitly ignored
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 1200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)

                then("it should return null for the latency stats and an empty list for the logs") {
                    val response = monitorClient.getMonitorStats(monitorId = monitor.id, latencyLogLimit = null)
                    response.id shouldBe monitor.id
                    response.latencyHistoryEnabled shouldBe false
                    response.averageLatencyInMs shouldBe null
                    response.p95LatencyInMs shouldBe null
                    response.p99LatencyInMs shouldBe null
                    response.latencyLogs.shouldBeEmpty()
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v1/monitors/1232132432/stats").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's createMonitor() endpoint") {

            `when`("it is called with a valid DTO - default parameters") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)

                then("it should create a monitor and also schedule checks for it") {

                    val monitorInDb = monitorRepository.findById(createdMonitor.id)!!
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe true
                    monitorInDb.enabled shouldBe createdMonitor.enabled
                    monitorInDb.sslCheckEnabled shouldBe false
                    monitorInDb.sslCheckEnabled shouldBe createdMonitor.sslCheckEnabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.pagerdutyIntegrationKey shouldBe null
                    monitorInDb.pagerdutyIntegrationKey shouldBe monitorToCreate.pagerdutyIntegrationKey
                    monitorInDb.requestMethod shouldBe HttpMethod.GET
                    monitorInDb.requestMethod shouldBe createdMonitor.requestMethod
                    monitorInDb.latencyHistoryEnabled shouldBe true
                    monitorInDb.latencyHistoryEnabled shouldBe createdMonitor.latencyHistoryEnabled
                    monitorInDb.forceNoCache shouldBe true
                    monitorInDb.forceNoCache shouldBe createdMonitor.forceNoCache
                    monitorInDb.followRedirects shouldBe true
                    monitorInDb.followRedirects shouldBe createdMonitor.followRedirects
                    monitorInDb.sslExpiryThreshold shouldBe 30
                    monitorInDb.sslExpiryThreshold shouldBe createdMonitor.sslExpiryThreshold

                    checkScheduler.getScheduledUptimeChecks()[createdMonitor.id].shouldNotBeNull()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("it is called with a valid DTO - explicit parameters") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor2",
                    url = "https://valid-url2.com",
                    uptimeCheckInterval = 65,
                    enabled = false,
                    sslCheckEnabled = true,
                    pagerdutyIntegrationKey = "something",
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = false,
                    forceNoCache = false,
                    followRedirects = false,
                    sslExpiryThreshold = 20,
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)

                then("it should create a monitor and also schedule checks for it") {
                    val monitorInDb = monitorRepository.findById(createdMonitor.id)!!
                    monitorInDb.name shouldBe "test_monitor2"
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe "https://valid-url2.com"
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe 65
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe false
                    monitorInDb.enabled shouldBe createdMonitor.enabled
                    monitorInDb.sslCheckEnabled shouldBe true
                    monitorInDb.sslCheckEnabled shouldBe createdMonitor.sslCheckEnabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.pagerdutyIntegrationKey shouldBe "something"
                    monitorInDb.requestMethod shouldBe HttpMethod.HEAD
                    monitorInDb.requestMethod shouldBe createdMonitor.requestMethod
                    monitorInDb.latencyHistoryEnabled shouldBe false
                    monitorInDb.latencyHistoryEnabled shouldBe createdMonitor.latencyHistoryEnabled
                    monitorInDb.forceNoCache shouldBe false
                    monitorInDb.forceNoCache shouldBe createdMonitor.forceNoCache
                    monitorInDb.followRedirects shouldBe false
                    monitorInDb.followRedirects shouldBe createdMonitor.followRedirects
                    monitorInDb.sslExpiryThreshold shouldBe 20
                    monitorInDb.sslExpiryThreshold shouldBe createdMonitor.sslExpiryThreshold

                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("there is already a monitor with the same name") {
                val firstMonitor = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true
                )
                val secondMonitor = MonitorCreateDto(
                    name = firstMonitor.name,
                    url = "https://valid-url2.com",
                    uptimeCheckInterval = 4000,
                    enabled = false
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstMonitor)
                val secondRequest = HttpRequest.POST("/api/v1/monitors", secondMonitor)
                val secondResponse = shouldThrow<HttpClientResponseException> {
                    client.exchange(secondRequest).awaitFirst()
                }

                then("it should return a 409") {
                    secondResponse.status shouldBe HttpStatus.CONFLICT
                    val monitorsInDb = monitorRepository.findByName(firstCreatedMonitor.name)
                    monitorsInDb.shouldNotBeNull()
                    checkScheduler.getScheduledUptimeChecks()[firstCreatedMonitor.id].shouldNotBeNull()
                }
            }

            `when`("it is called with an invalid URL") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "htt://invalid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true
                )
                val request = HttpRequest.POST("/api/v1/monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "url: must match \"^(https?)"
                }
            }

            `when`("it is called with an invalid uptime check interval") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 59,
                    enabled = true
                )
                val request = HttpRequest.POST("/api/v1/monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "uptimeCheckInterval: must be greater than or equal to 60"
                }
            }

            `when`("it is called with an invalid SSL expiry threshold") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    sslExpiryThreshold = -1
                )
                val request = HttpRequest.POST("/api/v1/monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request).awaitFirst()
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "sslExpiryThreshold: must be greater than or equal to 0"
                }
            }
        }

        given("MonitorController's deleteMonitor() endpoint") {

            `when`("it is called with an existing monitor ID") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v1/monitors/${createdMonitor.id}")
                val response = client.exchange(deleteRequest).awaitFirst()
                val monitorInDb = monitorRepository.findById(createdMonitor.id)

                then("it should delete the monitor and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val deleteRequest = HttpRequest.DELETE<Any>("/api/v1/monitors/123232")
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(deleteRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's updateMonitor() endpoint") {

            `when`("it is called with an existing monitor ID and a valid DTO to update all of the values") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    sslCheckEnabled = true,
                    pagerdutyIntegrationKey = "something",
                    followRedirects = true,
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = true,
                    forceNoCache = true,
                    sslExpiryThreshold = 10,
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                checkScheduler.getScheduledUptimeChecks()[createdMonitor.id].shouldNotBeNull()
                checkScheduler.getScheduledSSLChecks()[createdMonitor.id].shouldNotBeNull()

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::enabled.name, false)
                    .put(MonitorUpdateDto::sslCheckEnabled.name, false)
                    .put(MonitorUpdateDto::requestMethod.name, "GET")
                    .put(MonitorUpdateDto::latencyHistoryEnabled.name, false)
                    .put(MonitorUpdateDto::forceNoCache.name, false)
                    .put(MonitorUpdateDto::followRedirects.name, false)
                    .putNull(MonitorUpdateDto::pagerdutyIntegrationKey.name)
                    .put(MonitorUpdateDto::name.name, "updated_test_monitor")
                    .put(MonitorUpdateDto::url.name, "https://updated-url.com")
                    .put(MonitorUpdateDto::uptimeCheckInterval.name, "5000")
                    .put(MonitorUpdateDto::sslExpiryThreshold.name, "20")

                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and remove the checks of it") {
                    monitorInDb.name shouldBe "updated_test_monitor"
                    monitorInDb.url shouldBe "https://updated-url.com"
                    monitorInDb.uptimeCheckInterval shouldBe 5000
                    monitorInDb.enabled shouldBe false
                    monitorInDb.sslCheckEnabled shouldBe false
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null
                    monitorInDb.pagerdutyIntegrationKey shouldBe null // <- "Deleting" a nullable prop should work!
                    monitorInDb.requestMethod shouldBe HttpMethod.GET
                    monitorInDb.latencyHistoryEnabled shouldBe false
                    monitorInDb.forceNoCache shouldBe false
                    monitorInDb.followRedirects shouldBe false
                    monitorInDb.sslExpiryThreshold shouldBe 20

                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("it is called with an existing monitor ID and a valid DTO to enable the monitor") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = false,
                    pagerdutyIntegrationKey = "something"
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                checkScheduler.getScheduledSSLChecks().shouldBeEmpty()

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::enabled.name, true)
                    .put(MonitorUpdateDto::sslCheckEnabled.name, true)
                    .put(MonitorUpdateDto::requestMethod.name, "HEAD")
                    .put(MonitorUpdateDto::latencyHistoryEnabled.name, false)
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and create the checks of it and update only the present props") {
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe true
                    monitorInDb.sslCheckEnabled shouldBe true
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null
                    monitorInDb.pagerdutyIntegrationKey shouldBe createDto.pagerdutyIntegrationKey
                    monitorInDb.requestMethod shouldBe HttpMethod.HEAD
                    monitorInDb.latencyHistoryEnabled shouldBe false
                    monitorInDb.forceNoCache shouldBe createdMonitor.forceNoCache
                    monitorInDb.followRedirects shouldBe createdMonitor.followRedirects
                    monitorInDb.sslExpiryThreshold shouldBe createdMonitor.sslExpiryThreshold

                    checkScheduler.getScheduledUptimeChecks()[createdMonitor.id].shouldNotBeNull()
                    checkScheduler.getScheduledSSLChecks()[createdMonitor.id].shouldNotBeNull()
                }
            }

            `when`("it is called to disable the latency history and there are previous latency logs") {

                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    latencyHistoryEnabled = true
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                latencyLogRepository.insertLatencyForMonitor(createdMonitor.id, 1200)
                latencyLogRepository.fetchLatestByMonitorId(createdMonitor.id).shouldNotBeEmpty()

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::latencyHistoryEnabled.name, false)
                monitorClient.updateMonitor(createdMonitor.id, updateDto)

                then("it should remove the existing latency log records as well") {
                    latencyLogRepository.fetchLatestByMonitorId(createdMonitor.id).shouldBeEmpty()
                }
            }

            `when`("it is called with an existing monitor ID but there is an other monitor with the given name") {
                val firstCreateDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val firstCreatedMonitor = monitorClient.createMonitor(firstCreateDto)
                val secondCreateDto = MonitorCreateDto(
                    name = "test_monitor2",
                    url = "https://valid-url2.com",
                    uptimeCheckInterval = 6000
                )
                val secondCreatedMonitor = monitorClient.createMonitor(secondCreateDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::name.name, secondCreatedMonitor.name)
                val updateRequest =
                    HttpRequest.PATCH("/api/v1/monitors/${firstCreatedMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(firstCreatedMonitor.id)!!

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    monitorInDb.name shouldBe firstCreatedMonitor.name
                }
            }

            `when`("it is called with a blank name") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::name.name, "\n")
                val updateRequest =
                    HttpRequest.PATCH("/api/v1/monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain "Validation failed: name: must not be blank"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a null on a property that is non-nullable") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .putNull(MonitorUpdateDto::enabled.name)
                val updateRequest =
                    HttpRequest.PATCH("/api/v1/monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain "Validation failed: enabled: must not be null"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a too short uptime check interval") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::uptimeCheckInterval.name, 59)
                val updateRequest =
                    HttpRequest.PATCH("/api/v1/monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain
                        "Validation failed: uptimeCheckInterval: must be greater than or equal to 60"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with an invalid URL") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::url.name, "h34l/2683")
                val updateRequest =
                    HttpRequest.PATCH("/api/v1/monitors/${createdMonitor.id}", updateDto)
                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should return a 400 with a validation error") {
                    ex.status shouldBe HttpStatus.BAD_REQUEST
                    ex.response.getBodyAs<String>() shouldContain "Validation failed: url: must match"
                    monitorInDb.name shouldBe createdMonitor.name
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MonitorUpdateDto::enabled.name, false)
                val updateRequest = HttpRequest.PATCH("/api/v1/monitors/123232", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getUptimeEvents() endpoint") {
            `when`("there is a monitor with the given ID in the database with uptime events") {
                val monitor = createMonitor(monitorRepository)
                val anotherMonitor =
                    createMonitor(monitorRepository, monitorName = "another_monitor")
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusDays(1),
                    status = UptimeStatus.DOWN,
                    endedAt = now
                )
                createUptimeEventRecord(
                    dslContext,
                    monitorId = anotherMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )

                then("it should return its uptime events") {
                    val response = monitorClient.getUptimeEvents(monitorId = monitor.id)
                    response shouldHaveSize 2
                    response.forAll { it.id.shouldBeGreaterThan(0) }
                    response.forOne { it.status shouldBe UptimeStatus.UP }
                    response.forOne { it.status shouldBe UptimeStatus.DOWN }
                }
            }

            `when`("there is a monitor with the given ID in the database without uptime events") {
                val monitor = createMonitor(monitorRepository)

                then("it should return an empty list") {
                    val response = monitorClient.getUptimeEvents(monitorId = monitor.id)
                    response shouldHaveSize 0
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v1/monitors/1232132432/uptime-events").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getSSLEvents() endpoint") {
            `when`("there is a monitor with the given ID in the database with SSL events") {
                val monitor = createMonitor(monitorRepository)
                val anotherMonitor =
                    createMonitor(monitorRepository, monitorName = "another_monitor")
                val now = getCurrentTimestamp()
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = SslStatus.VALID,
                    endedAt = null
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = now.minusDays(1),
                    status = SslStatus.INVALID,
                    endedAt = now
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = anotherMonitor.id,
                    startedAt = now,
                    status = SslStatus.VALID,
                    endedAt = null
                )

                then("it should return its SSL events") {
                    val response = monitorClient.getSSLEvents(monitorId = monitor.id)
                    response shouldHaveSize 2
                    response.forAll { it.id.shouldBeGreaterThan(0) }
                    response.forOne { it.status shouldBe SslStatus.VALID }
                    response.forOne { it.status shouldBe SslStatus.INVALID }
                }
            }

            `when`("there is a monitor with the given ID in the database without ssl events") {
                val monitor = createMonitor(monitorRepository)

                then("it should return an empty list") {
                    val response = monitorClient.getSSLEvents(monitorId = monitor.id)
                    response shouldHaveSize 0
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v1/monitors/1232132432/ssl-events").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getMonitorsExport() endpoint") {
            val mapper = YAMLMapper()
                .registerModules(kotlinModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

            `when`("there are monitors in the database") {
                val monitor = createMonitor(
                    monitorRepository,
                    pagerdutyIntegrationKey = "something",
                    monitorName = "irrelevant",
                )
                val monitor2 = createMonitor(
                    monitorRepository,
                    enabled = false,
                    uptimeCheckInterval = 23234,
                    monitorName = "irrelevant2",
                    sslExpiryThreshold = 15,
                )
                val request = HttpRequest.GET<Any>("/api/v1/monitors/export").accept(MediaType.APPLICATION_YAML)

                then("it should export them in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBody(ByteArray::class.java).get()

                    response.status shouldBe HttpStatus.OK
                    with(response.headers[HttpHeaders.CONTENT_DISPOSITION]) {
                        this shouldContain "attachment;"
                        this shouldContain Regex("filename=\"kuvasz-monitors-export-\\d+\\.yml\"")
                    }
                    response.headers[HttpHeaders.CONTENT_TYPE] shouldBe MediaType.APPLICATION_YAML

                    val exportedMonitorsRaw = mapper.readTree(responseBody)["monitors"].shouldNotBeNull()
                    val parsedMonitors =
                        mapper.convertValue<List<MonitorExportDto>>(exportedMonitorsRaw).shouldNotBeEmpty()

                    parsedMonitors.size shouldBe 2
                    parsedMonitors.forOne { firstMonitor ->
                        firstMonitor.name shouldBe monitor.name
                        firstMonitor.url shouldBe monitor.url
                        firstMonitor.uptimeCheckInterval shouldBe monitor.uptimeCheckInterval
                        firstMonitor.enabled shouldBe monitor.enabled
                        firstMonitor.sslCheckEnabled shouldBe monitor.sslCheckEnabled
                        firstMonitor.pagerdutyIntegrationKey shouldBe monitor.pagerdutyIntegrationKey
                        firstMonitor.requestMethod shouldBe monitor.requestMethod
                        firstMonitor.latencyHistoryEnabled shouldBe monitor.latencyHistoryEnabled
                        firstMonitor.forceNoCache shouldBe monitor.forceNoCache
                        firstMonitor.followRedirects shouldBe monitor.followRedirects
                        firstMonitor.sslExpiryThreshold shouldBe monitor.sslExpiryThreshold
                    }
                    parsedMonitors.forOne { secondMonitor ->
                        secondMonitor.name shouldBe monitor2.name
                        secondMonitor.url shouldBe monitor2.url
                        secondMonitor.uptimeCheckInterval shouldBe monitor2.uptimeCheckInterval
                        secondMonitor.enabled shouldBe monitor2.enabled
                        secondMonitor.sslCheckEnabled shouldBe monitor2.sslCheckEnabled
                        secondMonitor.pagerdutyIntegrationKey shouldBe null
                        secondMonitor.requestMethod shouldBe monitor2.requestMethod
                        secondMonitor.latencyHistoryEnabled shouldBe monitor2.latencyHistoryEnabled
                        secondMonitor.forceNoCache shouldBe monitor2.forceNoCache
                        secondMonitor.followRedirects shouldBe monitor2.followRedirects
                        secondMonitor.sslExpiryThreshold shouldBe monitor2.sslExpiryThreshold
                    }
                }
            }

            `when`("there are no monitors in the database") {

                val request = HttpRequest.GET<Any>("/api/v1/monitors/export").accept(MediaType.APPLICATION_YAML)

                then("it should export an empty monitors list in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedMonitorsRaw = mapper.readTree(responseBody)["monitors"].shouldNotBeNull()
                    mapper.convertValue<List<MonitorExportDto>>(exportedMonitorsRaw).shouldBeEmpty()
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }
}
