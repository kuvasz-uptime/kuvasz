package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class MonitorControllerTest(
    @Client("/") private val client: RxHttpClient,
    private val monitorClient: MonitorClient,
    private val monitorRepository: MonitorRepository
) : DatabaseBehaviorSpec({

    given("MonitorController's getMonitors() endpoint") {
        `when`("there is any monitor in the database") {
            val monitor = createMonitor(monitorRepository)
            val response = monitorClient.getMonitors(enabledOnly = false)
            then("it should return them") {
                response.size shouldBe 1
                val responseItem = response.first()
                responseItem.id shouldBe monitor.id
                responseItem.url.toString() shouldBe monitor.url
                responseItem.enabled shouldBe monitor.enabled
                responseItem.averageLatencyInMs shouldBe null
                responseItem.uptimeStatus shouldBe null
                responseItem.createdAt shouldBe monitor.createdAt
            }
        }

        `when`("enabledOnly parameter is set to true") {
            createMonitor(monitorRepository, enabled = false, monitorName = "name1")
            val enabledMonitor = createMonitor(monitorRepository, id = 11111, monitorName = "name2")
            val response = monitorClient.getMonitors(enabledOnly = true)
            then("it should not return disabled monitor") {
                response.size shouldBe 1
                val responseItem = response.first()
                responseItem.id shouldBe enabledMonitor.id
                responseItem.url.toString() shouldBe enabledMonitor.url
                responseItem.enabled shouldBe enabledMonitor.enabled
                responseItem.averageLatencyInMs shouldBe null
                responseItem.uptimeStatus shouldBe null
                responseItem.createdAt shouldBe enabledMonitor.createdAt
            }
        }

        `when`("there isn't any monitor in the database") {
            val response = monitorClient.getMonitors(enabledOnly = false)
            then("it should return an empty list") {
                response.size shouldBe 0
            }
        }
    }

    given("MonitorController's getMonitor() endpoint") {
        `when`("there is a monitor with the given ID in the database") {
            val monitor = createMonitor(monitorRepository)
            val response = monitorClient.getMonitor(monitorId = monitor.id)
            then("it should return it") {
                response.id shouldBe monitor.id
                response.url.toString() shouldBe monitor.url
                response.enabled shouldBe monitor.enabled
                response.averageLatencyInMs shouldBe null
                response.uptimeStatus shouldBe null
                response.createdAt shouldBe monitor.createdAt
            }
        }

        `when`("there is no monitor with the given ID in the database") {
            val exception = shouldThrow<HttpClientResponseException> {
                client.toBlocking().exchange<Any>("/monitor/1232132432")
            }
            then("it should return a 404") {
                exception.status shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
})
