package com.kuvaszuptime.kuvasz.controllers

import arrow.core.Option
import arrow.core.toOption
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.CheckScheduler
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class MonitorControllerTest(
    @Client("/") private val client: RxHttpClient,
    private val monitorClient: MonitorClient,
    private val monitorRepository: MonitorRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val checkScheduler: CheckScheduler
) : DatabaseBehaviorSpec() {

    init {
        given("MonitorController's getMonitorsWithDetails() endpoint") {
            `when`("there is any monitor in the database") {
                val monitor = createMonitor(monitorRepository)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 1200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 600)

                val response = monitorClient.getMonitorsWithDetails(enabledOnly = null)
                then("it should return them") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe monitor.id
                    responseItem.name shouldBe monitor.name
                    responseItem.url.toString() shouldBe monitor.url
                    responseItem.enabled shouldBe monitor.enabled
                    responseItem.averageLatencyInMs shouldBe 800
                    responseItem.p95LatencyInMs shouldBe 1200
                    responseItem.p99LatencyInMs shouldBe 1200
                    responseItem.uptimeStatus shouldBe null
                    responseItem.createdAt shouldBe monitor.createdAt
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
                    responseItem.averageLatencyInMs shouldBe null
                    responseItem.uptimeStatus shouldBe null
                    responseItem.createdAt shouldBe enabledMonitor.createdAt
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
                val monitor = createMonitor(monitorRepository)
                val response = monitorClient.getMonitorDetails(monitorId = monitor.id)
                then("it should return it") {
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.url.toString() shouldBe monitor.url
                    response.enabled shouldBe monitor.enabled
                    response.averageLatencyInMs shouldBe null
                    response.uptimeStatus shouldBe null
                    response.createdAt shouldBe monitor.createdAt
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any>("/monitor/1232132432/details")
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's getMonitors() endpoint") {
            `when`("there is any monitor in the database") {
                val monitor = createMonitor(monitorRepository)
                val response = monitorClient.getMonitors(enabledOnly = null)
                then("it should return them") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe monitor.id
                    responseItem.name shouldBe monitor.name
                    responseItem.url shouldBe monitor.url
                    responseItem.enabled shouldBe monitor.enabled
                    responseItem.createdAt shouldBe monitor.createdAt
                }
            }

            `when`("enabledOnly parameter is set to true") {
                createMonitor(monitorRepository, enabled = false, monitorName = "name1")
                val enabledMonitor = createMonitor(monitorRepository, id = 11111, monitorName = "name2")
                val response = monitorClient.getMonitors(enabledOnly = true)
                then("it should not return disabled monitor") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe enabledMonitor.id
                    responseItem.name shouldBe enabledMonitor.name
                    responseItem.url.toString() shouldBe enabledMonitor.url
                    responseItem.enabled shouldBe enabledMonitor.enabled
                    responseItem.createdAt shouldBe enabledMonitor.createdAt
                }
            }

            `when`("there isn't any monitor in the database") {
                val response = monitorClient.getMonitorsWithDetails(enabledOnly = false)
                then("it should return an empty list") {
                    response shouldHaveSize 0
                }
            }
        }

        given("MonitorController's getMonitor() endpoint") {
            `when`("there is a monitor with the given ID in the database") {
                val monitor = createMonitor(monitorRepository)
                val response = monitorClient.getMonitor(monitorId = monitor.id)
                then("it should return it") {
                    response.id shouldBe monitor.id
                    response.name shouldBe monitor.name
                    response.url.toString() shouldBe monitor.url
                    response.enabled shouldBe monitor.enabled
                    response.createdAt shouldBe monitor.createdAt
                }
            }

            `when`("there is no monitor with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<Any>("/monitor/1232132432")
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("MonitorController's createMonitor() endpoint") {

            `when`("it is called with a valid DTO") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000
                )
                val createdMonitor = monitorClient.createMonitor(monitorToCreate)

                then("it should create a monitor and also schedule checks for it") {
                    val monitorInDb = monitorRepository.findById(createdMonitor.id)!!
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe createdMonitor.enabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    checkScheduler.getScheduledChecks().forOne { it.monitorId shouldBe createdMonitor.id }
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
                val secondRequest = HttpRequest.POST("/monitor", secondMonitor)
                val secondResponse = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorCreateDto, Any>(secondRequest)
                }

                then("it should return a 409") {
                    secondResponse.status shouldBe HttpStatus.CONFLICT
                    val monitorsInDb = monitorRepository.fetchByName(firstCreatedMonitor.name)
                    monitorsInDb shouldHaveSize 1
                    checkScheduler.getScheduledChecks().forOne { it.monitorId shouldBe firstCreatedMonitor.id }
                }
            }

            `when`("it is called with an invalid URL") {
                val monitorToCreate = MonitorCreateDto(
                    name = "test_monitor",
                    url = "htt://invalid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = true
                )
                val request = HttpRequest.POST("/monitor", monitorToCreate)
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
                val request = HttpRequest.POST("/monitor", monitorToCreate)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorCreateDto, Any>(request)
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "uptimeCheckInterval: must be greater than or equal to 60"
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
                val deleteRequest = HttpRequest.DELETE<Any>("/monitor/${createdMonitor.id}")
                val response = client.toBlocking().exchange<Any, Any>(deleteRequest)
                val monitorInDb = monitorRepository.findById(createdMonitor.id).toOption()

                then("it should delete the monitor and also remove the checks of it") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    monitorInDb shouldBe Option.empty()

                    checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe createdMonitor.id }
                }
            }

            `when`("it is called with a non existing monitor ID") {
                val deleteRequest = HttpRequest.DELETE<Any>("/monitor/123232")
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
                    enabled = true
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                checkScheduler.getScheduledChecks().forOne { it.monitorId shouldBe createdMonitor.id }

                val updateDto = MonitorUpdateDto(
                    name = "updated_test_monitor",
                    url = "https://updated-url.com",
                    uptimeCheckInterval = 5000,
                    enabled = false
                )
                val updatedMonitor = monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and remove the checks of it") {
                    monitorInDb.name shouldBe updatedMonitor.name
                    monitorInDb.url shouldBe updatedMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe updatedMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe updatedMonitor.enabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null

                    checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe createdMonitor.id }
                }
            }

            `when`("it is called with an existing monitor ID and a valid DTO to enable the monitor") {
                val createDto = MonitorCreateDto(
                    name = "test_monitor",
                    url = "https://valid-url.com",
                    uptimeCheckInterval = 6000,
                    enabled = false
                )
                val createdMonitor = monitorClient.createMonitor(createDto)
                checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe createdMonitor.id }

                val updateDto = MonitorUpdateDto(
                    name = null,
                    url = null,
                    uptimeCheckInterval = null,
                    enabled = true
                )
                val updatedMonitor = monitorClient.updateMonitor(createdMonitor.id, updateDto)
                val monitorInDb = monitorRepository.findById(createdMonitor.id)!!

                then("it should update the monitor and create the checks of it") {
                    monitorInDb.name shouldBe createdMonitor.name
                    monitorInDb.url shouldBe createdMonitor.url
                    monitorInDb.uptimeCheckInterval shouldBe createdMonitor.uptimeCheckInterval
                    monitorInDb.enabled shouldBe updatedMonitor.enabled
                    monitorInDb.createdAt shouldBe createdMonitor.createdAt
                    monitorInDb.updatedAt shouldNotBe null

                    checkScheduler.getScheduledChecks().forOne { it.monitorId shouldBe createdMonitor.id }
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
                    enabled = true
                )
                val updateRequest = HttpRequest.PATCH<MonitorUpdateDto>("/monitor/${firstCreatedMonitor.id}", updateDto)
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
                val updateDto = MonitorUpdateDto(null, null, null, null)
                val updateRequest = HttpRequest.PATCH<MonitorUpdateDto>("/monitor/123232", updateDto)
                val response = shouldThrow<HttpClientResponseException> {
                    client.toBlocking().exchange<MonitorUpdateDto, Any>(updateRequest)
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }
}
