package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.reactivex.rxjava3.subscribers.TestSubscriber
import java.net.URI

@MicronautTest(startApplication = false)
class UptimeCheckerTest(
    uptimeChecker: UptimeChecker,
    private val monitorRepository: MonitorRepository,
    private val eventDispatcher: EventDispatcher
) : DatabaseBehaviorSpec() {
    init {
        val uptimeCheckerSpy = spyk(uptimeChecker)

        given("the UptimeChecker service") {
            `when`("it checks a monitor that is UP - GET") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is UP - HEAD") {
                val monitor = createMonitor(monitorRepository, requestMethod = HttpMethod.HEAD)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is UP - forceNoCache is false") {
                val monitor = createMonitor(monitorRepository, forceNoCache = false)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN") {
                val monitor = createMonitor(monitorRepository, url = "http://this-should-not.exist")
                val subscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.GATEWAY_TIMEOUT)

                then("it should dispatch a MonitorDownEvent") {
                    uptimeCheckerSpy.check(monitor)

                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN but then it's UP again") {
                val monitor = createMonitor(monitorRepository, followRedirects = false)
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.NOT_FOUND)

                then("it should dispatch a MonitorDownEvent and a MonitorUpEvent") {
                    uptimeCheckerSpy.check(monitor)
                    clearAllMocks()
                    mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)
                    uptimeCheckerSpy.check(monitor)

                    val expectedDownEvent = monitorDownSubscriber.awaitCount(1).values().first()
                    val expectedUpEvent = monitorUpSubscriber.awaitCount(1).values().first()

                    expectedDownEvent.monitor.id shouldBe monitor.id
                    expectedUpEvent.monitor.id shouldBe monitor.id
                    expectedDownEvent.dispatchedAt shouldBeLessThan expectedUpEvent.dispatchedAt
                }
            }

            `when`("it checks a monitor that is UP but then it's DOWN again") {
                val monitor = createMonitor(monitorRepository)
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                then("it should dispatch a MonitorUpEvent and a MonitorDownEvent") {
                    uptimeCheckerSpy.check(monitor)
                    clearAllMocks()
                    mockHttpResponse(uptimeCheckerSpy, HttpStatus.NOT_FOUND)
                    uptimeCheckerSpy.check(monitor)

                    val expectedDownEvent = monitorDownSubscriber.awaitCount(1).values().first()
                    val expectedUpEvent = monitorUpSubscriber.awaitCount(1).values().first()
                    expectedDownEvent.monitor.id shouldBe monitor.id
                    expectedUpEvent.monitor.id shouldBe monitor.id
                    expectedDownEvent.dispatchedAt shouldBeGreaterThan expectedUpEvent.dispatchedAt
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockHttpResponse(
        uptimeChecker: UptimeChecker,
        httpStatus: HttpStatus,
        requestUri: URI? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
    ) {
        val response = SimpleHttpResponseFactory()
            .status<ByteBuffer<Any>>(httpStatus)
            .headers { headers ->
                additionalHeaders.forEach { (name, value) ->
                    headers.add(name, value)
                }
            }
        every {
            uptimeChecker["sendHttpRequest"](
                any<MonitorRecord>(),
                requestUri ?: any<URI>()
            )
        } returns HttpCheckResponse(httpResponse = response, latency = 100)
    }
}
