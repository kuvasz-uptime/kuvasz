package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpUptimeChecker
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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
class HttpUptimeCheckerTest(
    uptimeChecker: HttpUptimeChecker,
    private val monitorRepository: HttpMonitorRepository,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
) : DatabaseBehaviorSpec() {
    init {
        val uptimeCheckerSpy = spyk(uptimeChecker)

        given("the UptimeChecker service") {
            `when`("it checks a monitor that is UP - GET") {
                val monitor = createHttpMonitor(monitorRepository)
                val subscriber = TestSubscriber<HttpMonitorUpEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent and insert a latency record") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldHaveSize(1)
                }
            }

            `when`("it checks a monitor that is UP - latency history disabled") {
                val monitor = createHttpMonitor(monitorRepository, latencyHistoryEnabled = false)
                val subscriber = TestSubscriber<HttpMonitorUpEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent but not insert a latency record") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("it checks a monitor that is UP - HEAD") {
                val monitor = createHttpMonitor(monitorRepository, requestMethod = HttpMethod.HEAD)
                val subscriber = TestSubscriber<HttpMonitorUpEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent and insert a latency record") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldHaveSize(1)
                }
            }

            `when`("it checks a monitor that is UP - forceNoCache is false") {
                val monitor = createHttpMonitor(monitorRepository, forceNoCache = false)
                val subscriber = TestSubscriber<HttpMonitorUpEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN") {
                val monitor = createHttpMonitor(monitorRepository, url = "http://this-should-not.exist")
                val subscriber = TestSubscriber<HttpMonitorDownEvent>()
                eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.GATEWAY_TIMEOUT)

                then("it should dispatch a MonitorDownEvent") {
                    uptimeCheckerSpy.check(monitor)

                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN but then it's UP again") {
                val monitor = createHttpMonitor(monitorRepository, followRedirects = false)
                val monitorUpSubscriber = TestSubscriber<HttpMonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<HttpMonitorDownEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
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
                val monitor = createHttpMonitor(monitorRepository)
                val monitorUpSubscriber = TestSubscriber<HttpMonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<HttpMonitorDownEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
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

            `when`("it checks a monitor that has multiple DOWN events due to a race condition - an UP received") {
                val monitor = createHttpMonitor(monitorRepository)
                val monitorUpSubscriber = TestSubscriber<HttpMonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<HttpMonitorDownEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                val firstRecord = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusMinutes(10),
                    endedAt = null,
                )
                val secondRecord = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusMinutes(10).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the unnecessary, older event, and update the other one") {
                    uptimeCheckerSpy.check(monitor)

                    monitorDownSubscriber.assertNoValues()
                    val expectedUpEvent = monitorUpSubscriber.awaitCount(1).values().first()
                    expectedUpEvent.monitor.id shouldBe monitor.id

                    uptimeEventRepository.fetchByMonitorId(monitor.id)
                        .shouldHaveSize(2)
                        .forOne { relevantRecord ->
                            relevantRecord.id shouldBe secondRecord.id
                            relevantRecord.updatedAt shouldBeGreaterThan secondRecord.updatedAt
                        }
                        .forNone { it.id shouldBe firstRecord.id }
                }
            }

            `when`("it checks a monitor that has multiple DOWN events due to a race condition - a DOWN received") {
                val monitor = createHttpMonitor(monitorRepository)
                val monitorUpSubscriber = TestSubscriber<HttpMonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<HttpMonitorDownEvent>()
                eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.NOT_FOUND)

                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusMinutes(10),
                    endedAt = null,
                )
                val secondRecord = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusMinutes(10).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the unnecessary, older event, and update the other one") {
                    uptimeCheckerSpy.check(monitor)

                    monitorUpSubscriber.assertNoValues()
                    val expectedDownEvent = monitorDownSubscriber.awaitCount(1).values().first()
                    expectedDownEvent.monitor.id shouldBe monitor.id

                    uptimeEventRepository.fetchByMonitorId(monitor.id)
                        .shouldHaveSize(1)
                        .forOne { relevantRecord ->
                            relevantRecord.id shouldBe secondRecord.id
                            relevantRecord.updatedAt shouldBeGreaterThan secondRecord.updatedAt
                        }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockHttpResponse(
        uptimeChecker: HttpUptimeChecker,
        httpStatus: HttpStatus,
        requestUri: URI? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
    ) {
        val response = SimpleHttpResponseFactory()
            .status<ByteBuffer<*>>(httpStatus)
            .headers { headers ->
                additionalHeaders.forEach { (name, value) ->
                    headers.add(name, value)
                }
            }
        every {
            uptimeChecker["sendHttpRequest"](
                any<HttpMonitorRecord>(),
                requestUri ?: any<URI>()
            )
        } returns HttpCheckResponse(httpResponse = response, latency = 100)
    }
}
