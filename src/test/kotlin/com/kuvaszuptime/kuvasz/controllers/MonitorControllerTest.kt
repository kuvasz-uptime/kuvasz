package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.PagerdutyKeyUpdateDto
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.CheckScheduler
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.rxjava3.http.client.Rx3HttpClient
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@Suppress("LongParameterList")
@MicronautTest
class MonitorControllerTest(
    @Client("/") private val client: Rx3HttpClient,
    private val monitorClient: MonitorClient,
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val checkScheduler: CheckScheduler
) : DatabaseBehaviorSpec() {

    init {
        given("MonitorController's getMonitorsWithDetails() endpoint") {
            `when`("there is a monitor in the database") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 1200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createSSLEventRecord(
                    repository = sslEventRepository,
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
                    responseItem.averageLatencyInMs shouldBe 800
                    responseItem.p95LatencyInMs shouldBe 1200
                    responseItem.p99LatencyInMs shouldBe 1200
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
                }
            }

            `when`("enabledOnly parameter is set to true") {
                createMonitor(monitorRepository, enabled = false, monitorName = "name1")
                val enabledMonitor = createMonitor(monitorRepository, id = 11111, monitorName = "name2")
                val response = monitorClient.getMonitorsWithDetails(enabledOnly = true)
                then("it should not return disabled monitor") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe enabledMonitor.id
                    responseItem.name shouldBe enabledMonitor.name
                    responseItem.url.toString() shouldBe enabledMonitor.url
                    responseItem.enabled shouldBe enabledMonitor.enabled
                    responseItem.sslCheckEnabled shouldBe enabledMonitor.sslCheckEnabled
                    responseItem.averageLatencyInMs shouldBe null
                    responseItem.uptimeStatus shouldBe null
                    responseItem.sslStatus shouldBe null
                    responseItem.createdAt shouldBe enabledMonitor.createdAt
                    responseItem.pagerdutyKeyPresent shouldBe false
                    responseItem.requestMethod shouldBe HttpMethod.GET
                    responseItem.latencyHistoryEnabled shouldBe true
                    responseItem.forceNoCache shouldBe true
                    responseItem.followRedirects shouldBe true
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
                )
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 1200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createSSLEventRecord(
                    repository = sslEventRepository,
                    monitorId = monitor.id,
                    startedAt = now,
                    endedAt = null
                )

                then("it should return it") {
                    val response = monitorClient.getMonitorDetails(monitorId = monitor.id)
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.url.toString() shouldBe monitor.url
                    response.enabled shouldBe monitor.enabled
                    response.sslCheckEnabled shouldBe monitor.sslCheckEnabled
                    response.averageLatencyInMs shouldBe 800
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
                    response.p95LatencyInMs shouldNotBe null
                    response.p99LatencyInMs shouldNotBe null
                }
            }

            `when`("there is a monitor with the given ID in the database - latency history disabled") {
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

                then("it should return it, but without latency info") {
                    val response = monitorClient.getMonitorDetails(monitorId = monitor.id)
                    response.id shouldBe monitor.id
                    response.latencyHistoryEnabled shouldBe false
                    response.averageLatencyInMs shouldBe null
                    response.p95LatencyInMs shouldBe null
                    response.p99LatencyInMs shouldBe null
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any>("/monitors/1232132432")
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

                    checkScheduler.getScheduledChecks()
                        .filter { it.monitorId == createdMonitor.id }
                        .forOne { it.checkType shouldBe CheckType.UPTIME }
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
                    followRedirects = false
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

                    checkScheduler.getScheduledChecks()
                        .filter { it.monitorId == createdMonitor.id }
                        .shouldBeEmpty()
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
                val secondRequest = HttpRequest.POST("/monitors", secondMonitor)
                val secondResponse = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorCreateDto, Any>(secondRequest)
                }

                then("it should return a 409") {
                    secondResponse.status shouldBe HttpStatus.CONFLICT
                    val monitorsInDb = monitorRepository.fetchByName(firstCreatedMonitor.name)
                    monitorsInDb shouldHaveSize 1
                    checkScheduler.getScheduledChecks().filter { it.monitorId == firstCreatedMonitor.id }
                        .forOne { it.checkType shouldBe CheckType.UPTIME }
                }
            }

            `when`("it is called with an invalid URL") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "htt://invalid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true
                )
                val request = HttpRequest.POST("/monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorCreateDto, Any>(request)
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
                val request = HttpRequest.POST("/monitors", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorCreateDto, Any>(request)
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "uptimeCheckInterval: must be greater than or equal to 60"
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
                val deleteRequest = HttpRequest.DELETE<Any>("/monitors/${createdMonitor.id}")
                val response = client.toBlocking().exchange<Any, Any>(deleteRequest)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)

                then("it should delete the monitor and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe null

                    checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe createdMonitor.id }
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val deleteRequest = HttpRequest.DELETE<Any>("/monitors/123232")
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any, Any>(deleteRequest)
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's updateMonitor() endpoint") {

            `when`("it is called with an existing monitor ID and a valid DTO to disable the monitor") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    sslCheckEnabled = true,
                    pagerdutyIntegrationKey = "something"
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                val checks = checkScheduler.getScheduledChecks().filter { it.monitorId == createdMonitor.id }
                checks.forOne { it.checkType shouldBe CheckType.UPTIME }
                checks.forOne { it.checkType shouldBe CheckType.SSL }

                val updateDto = MonitorUpdateDto(
                    name = "updated_test_monitor",
                    url = "https://updated-url.com",
                    uptimeCheckInterval = 5000,
                    enabled = false,
                    sslCheckEnabled = false,
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = false,
                    forceNoCache = false,
                    followRedirects = false,
                )
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and remove the checks of it") {
                    monitorInDb.name shouldBe updateDto.name
                    monitorInDb.url shouldBe updateDto.url
                    monitorInDb.uptimeCheckInterval shouldBe updateDto.uptimeCheckInterval
                    monitorInDb.enabled shouldBe updateDto.enabled
                    monitorInDb.sslCheckEnabled shouldBe updateDto.sslCheckEnabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null
                    monitorInDb.pagerdutyIntegrationKey shouldBe createDto.pagerdutyIntegrationKey
                    monitorInDb.requestMethod shouldNotBe createdMonitor.requestMethod
                    monitorInDb.requestMethod shouldBe updateDto.requestMethod
                    monitorInDb.latencyHistoryEnabled shouldNotBe createdMonitor.latencyHistoryEnabled
                    monitorInDb.latencyHistoryEnabled shouldBe updateDto.latencyHistoryEnabled
                    monitorInDb.forceNoCache shouldNotBe createdMonitor.forceNoCache
                    monitorInDb.forceNoCache shouldBe updateDto.forceNoCache
                    monitorInDb.followRedirects shouldNotBe createdMonitor.followRedirects
                    monitorInDb.followRedirects shouldBe updateDto.followRedirects

                    val updatedChecks = checkScheduler.getScheduledChecks().filter { it.monitorId == createdMonitor.id }
                    updatedChecks.shouldBeEmpty()
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
                checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe createdMonitor.id }

                val updateDto = MonitorUpdateDto(
                    name = null,
                    url = null,
                    uptimeCheckInterval = null,
                    enabled = true,
                    sslCheckEnabled = true,
                    requestMethod = HttpMethod.HEAD,
                    latencyHistoryEnabled = false,
                    forceNoCache = false,
                    followRedirects = false,
                )
                monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and create the checks of it") {
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe updateDto.enabled
                    monitorInDb.sslCheckEnabled shouldBe updateDto.sslCheckEnabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null
                    monitorInDb.pagerdutyIntegrationKey shouldBe createDto.pagerdutyIntegrationKey
                    monitorInDb.requestMethod shouldBe updateDto.requestMethod
                    monitorInDb.latencyHistoryEnabled shouldBe updateDto.latencyHistoryEnabled
                    monitorInDb.forceNoCache shouldBe updateDto.forceNoCache
                    monitorInDb.followRedirects shouldBe updateDto.followRedirects

                    val checks = checkScheduler.getScheduledChecks().filter { it.monitorId == createdMonitor.id }
                    checks.forOne { it.checkType shouldBe CheckType.UPTIME }
                    checks.forOne { it.checkType shouldBe CheckType.SSL }
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
                latencyLogRepository.fetchByMonitorId(createdMonitor.id).shouldNotBeEmpty()

                val updateDto = MonitorUpdateDto(
                    name = null,
                    url = null,
                    uptimeCheckInterval = null,
                    enabled = null,
                    sslCheckEnabled = null,
                    requestMethod = null,
                    latencyHistoryEnabled = false,
                    forceNoCache = null,
                    followRedirects = null,
                )
                monitorClient.updateMonitor(createdMonitor.id, updateDto)

                then("it should remove the existing latency log records as well") {
                    latencyLogRepository.fetchByMonitorId(createdMonitor.id).shouldBeEmpty()
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

                val updateDto = MonitorUpdateDto(
                    name = secondCreatedMonitor.name,
                    url = null,
                    uptimeCheckInterval = null,
                    enabled = null,
                    sslCheckEnabled = null,
                    requestMethod = null,
                    latencyHistoryEnabled = null,
                    forceNoCache = null,
                    followRedirects = null,
                )
                val updateRequest =
                    HttpRequest.PATCH<MonitorUpdateDto>("/monitors/${firstCreatedMonitor.id}", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorUpdateDto, Any>(updateRequest)
                }
                val monitorInDb = monitorRepository.findById(firstCreatedMonitor.id)!!

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    monitorInDb.name shouldBe firstCreatedMonitor.name
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val updateDto = MonitorUpdateDto(
                    name = null,
                    url = null,
                    uptimeCheckInterval = null,
                    enabled = null,
                    sslCheckEnabled = null,
                    requestMethod = null,
                    latencyHistoryEnabled = null,
                    forceNoCache = null,
                    followRedirects = null,
                )
                val updateRequest = HttpRequest.PATCH<MonitorUpdateDto>("/monitors/123232", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorUpdateDto, Any>(updateRequest)
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's deletePagerdutyIntegrationKey() endpoint") {

            `when`("it is called with an existing monitor ID") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    pagerdutyIntegrationKey = "something"
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)
                val deleteRequest = HttpRequest.DELETE<Any>("/monitors/${createdMonitor.id}/pagerduty-integration-key")
                val response = client.toBlocking().exchange<Any, Any>(deleteRequest)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)

                then("it should delete the integration key of the given monitor") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb!!.pagerdutyIntegrationKey shouldBe null
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val deleteRequest = HttpRequest.DELETE<Any>("/monitors/123232/pagerduty-integration-key")
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any, Any>(deleteRequest)
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's upsertPagerdutyIntegrationKey() endpoint") {

            `when`("it is called with an existing monitor ID and an integration key") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    sslCheckEnabled = true,
                    pagerdutyIntegrationKey = "originalKey"
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = PagerdutyKeyUpdateDto(
                    pagerdutyIntegrationKey = "updatedKey"
                )
                val updatedMonitor = monitorClient.upsertPagerdutyIntegrationKey(createdMonitor.id, updateDto)
                val updatedMonitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the integration key") {
                    createdMonitor.pagerdutyKeyPresent shouldBe true
                    updatedMonitor.pagerdutyKeyPresent shouldBe true
                    updatedMonitorInDb.pagerdutyIntegrationKey shouldBe updateDto.pagerdutyIntegrationKey
                }
            }

            `when`("it is called with an existing monitor, without an integration key") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true,
                    sslCheckEnabled = true
                )
                val createdMonitor = monitorClient.createMonitor(createDto)

                val updateDto = PagerdutyKeyUpdateDto(
                    pagerdutyIntegrationKey = "updatedKey"
                )
                val updatedMonitor = monitorClient.upsertPagerdutyIntegrationKey(createdMonitor.id, updateDto)
                val updatedMonitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should create the integration key") {
                    createdMonitor.pagerdutyKeyPresent shouldBe false
                    updatedMonitor.pagerdutyKeyPresent shouldBe true
                    updatedMonitorInDb.pagerdutyIntegrationKey shouldBe updateDto.pagerdutyIntegrationKey
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val updateDto = PagerdutyKeyUpdateDto("something")
                val updateRequest =
                    HttpRequest.PUT<PagerdutyKeyUpdateDto>("/monitors/123232/pagerduty-integration-key", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<PagerdutyKeyUpdateDto, Any>(updateRequest)
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
                    createMonitor(monitorRepository, id = monitor.id + 1, monitorName = "another_monitor")
                val now = getCurrentTimestamp()
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = now.minusDays(1),
                    status = UptimeStatus.DOWN,
                    endedAt = now
                )
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = anotherMonitor.id,
                    startedAt = now,
                    status = UptimeStatus.UP,
                    endedAt = null
                )

                then("it should return its uptime events") {
                    val response = monitorClient.getUptimeEvents(monitorId = monitor.id)
                    response shouldHaveSize 2
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
                    client.toBlocking().exchange<Any>("/monitors/1232132432/uptime-events")
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
                    createMonitor(monitorRepository, id = monitor.id + 1, monitorName = "another_monitor")
                val now = getCurrentTimestamp()
                createSSLEventRecord(
                    repository = sslEventRepository,
                    monitorId = monitor.id,
                    startedAt = now,
                    status = SslStatus.VALID,
                    endedAt = null
                )
                createSSLEventRecord(
                    repository = sslEventRepository,
                    monitorId = monitor.id,
                    startedAt = now.minusDays(1),
                    status = SslStatus.INVALID,
                    endedAt = now
                )
                createSSLEventRecord(
                    repository = sslEventRepository,
                    monitorId = anotherMonitor.id,
                    startedAt = now,
                    status = SslStatus.VALID,
                    endedAt = null
                )

                then("it should return its uptime events") {
                    val response = monitorClient.getSSLEvents(monitorId = monitor.id)
                    response shouldHaveSize 2
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
                    client.toBlocking().exchange<Any>("/monitors/1232132432/ssl-events")
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }
}
