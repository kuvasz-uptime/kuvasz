package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.testutils.toSubscriber
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.reactivex.rxjava3.core.Flowable.fromArray
import io.reactivex.rxjava3.subscribers.TestSubscriber
import java.net.URI

@MicronautTest(startApplication = false)
class UptimeCheckerTest(
    uptimeChecker: UptimeChecker,
    private val monitorRepository: MonitorRepository,
    private val eventDispatcher: EventDispatcher
) : DatabaseBehaviorSpec() {
    init {
        val uptimeCheckerSpy = spyk(uptimeChecker, recordPrivateCalls = true)

        given("the UptimeChecker service") {
            `when`("it checks a monitor that is UP - GET") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.awaitCount(1)
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is UP - HEAD") {
                val monitor = createMonitor(monitorRepository, requestMethod = HttpMethod.HEAD)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.awaitCount(1)
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is UP - forceNoCache is false") {
                val monitor = createMonitor(monitorRepository, forceNoCache = false)
                val subscriber = TestSubscriber<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.awaitCount(1)
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN") {
                val monitor = createMonitor(monitorRepository, url = "http://this-should-not.exist")
                val subscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(subscriber) }

                then("it should dispatch a MonitorDownEvent") {
                    uptimeCheckerSpy.check(monitor)
                    val expectedEvent = subscriber.awaitCount(1).values().first()

                    subscriber.awaitCount(1)
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN but then it's UP again") {
                val monitor = createMonitor(monitorRepository, followRedirects = false)
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.NOT_FOUND)

                then("it should dispatch a MonitorDownEvent and a MonitorUpEvent") {
                    uptimeCheckerSpy.check(monitor)
                    clearAllMocks()
                    mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)
                    uptimeCheckerSpy.check(monitor)

                    val expectedDownEvent = monitorDownSubscriber.values().first()
                    val expectedUpEvent = monitorUpSubscriber.values().first()

                    monitorDownSubscriber.awaitCount(1)
                    monitorUpSubscriber.awaitCount(1)
                    expectedDownEvent.monitor.id shouldBe monitor.id
                    expectedUpEvent.monitor.id shouldBe monitor.id
                    expectedDownEvent.dispatchedAt shouldBeLessThan expectedUpEvent.dispatchedAt
                }
            }

            `when`("it checks a monitor that is UP but then it's DOWN again") {
                val monitor = createMonitor(monitorRepository)
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(monitorUpSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK)

                then("it should dispatch a MonitorUpEvent and a MonitorDownEvent") {
                    uptimeCheckerSpy.check(monitor)
                    clearAllMocks()
                    mockHttpResponse(uptimeCheckerSpy, HttpStatus.NOT_FOUND)
                    uptimeCheckerSpy.check(monitor)

                    val expectedDownEvent = monitorDownSubscriber.values().first()
                    val expectedUpEvent = monitorUpSubscriber.values().first()

                    monitorDownSubscriber.awaitCount(1)
                    monitorUpSubscriber.awaitCount(1)
                    expectedDownEvent.monitor.id shouldBe monitor.id
                    expectedUpEvent.monitor.id shouldBe monitor.id
                    expectedDownEvent.dispatchedAt shouldBeGreaterThan expectedUpEvent.dispatchedAt
                }
            }

            `when`("it checks a monitor that is redirected without a Location header") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(subscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.PERMANENT_REDIRECT)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a MonitorDownEvent") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.awaitCount(1)
                    expectedEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected with a Location header, but it's DOWN") {
                val monitor = createMonitor(monitorRepository)
                val redirectSubscriber = TestSubscriber<RedirectEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                val redirectLocation = "http://redirected-bad.loc"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)

                eventDispatcher.subscribeToRedirectEvents { it.toSubscriber(redirectSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.INTERNAL_SERVER_ERROR, redirectLocation.toUri())

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a RedirectEvent and then a MonitorDownEvent") {
                    val expectedRedirectEvent = redirectSubscriber.values().first()
                    val expectedDownEvent = monitorDownSubscriber.values().first()

                    redirectSubscriber.awaitCount(1)
                    expectedRedirectEvent.redirectLocation shouldBe redirectLocation.toUri()
                    expectedRedirectEvent.monitor.id shouldBe monitor.id

                    monitorDownSubscriber.awaitCount(1)
                    expectedDownEvent.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                    expectedDownEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected, but following redirects is disabled") {
                val monitor = createMonitor(monitorRepository, followRedirects = false)
                val redirectSubscriber = TestSubscriber<RedirectEvent>()
                val monitorDownSubscriber = TestSubscriber<MonitorDownEvent>()
                val redirectLocation = "http://redirected-bad.loc"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)

                eventDispatcher.subscribeToRedirectEvents { it.toSubscriber(redirectSubscriber) }
                eventDispatcher.subscribeToMonitorDownEvents { it.toSubscriber(monitorDownSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch ony a MonitorDownEvent") {
                    redirectSubscriber.values().shouldBeEmpty()
                    val expectedDownEvent = monitorDownSubscriber.values().first()

                    monitorDownSubscriber.awaitCount(1)
                    expectedDownEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                    expectedDownEvent.error.message shouldBe
                        "The request was redirected, but the followRedirects option is disabled"
                    expectedDownEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected with a Location header, but it's UP") {
                val monitor = createMonitor(monitorRepository)
                val redirectSubscriber = TestSubscriber<RedirectEvent>()
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val redirectLocation = "http://redirected-good.loc"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)

                eventDispatcher.subscribeToRedirectEvents { it.toSubscriber(redirectSubscriber) }
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(monitorUpSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK, redirectLocation.toUri())

                uptimeCheckerSpy.check(monitor)

                then("it should dispatch a RedirectEvent and then a MonitorUpEvent") {
                    val expectedRedirectEvent = redirectSubscriber.values().first()
                    val expectedUpEvent = monitorUpSubscriber.values().first()

                    redirectSubscriber.awaitCount(1)
                    expectedRedirectEvent.redirectLocation shouldBe redirectLocation.toUri()
                    expectedRedirectEvent.monitor.id shouldBe monitor.id

                    monitorUpSubscriber.awaitCount(1)
                    expectedUpEvent.status shouldBe HttpStatus.OK
                    expectedUpEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected with a relative Location header") {
                val monitor = createMonitor(monitorRepository)
                val redirectSubscriber = TestSubscriber<RedirectEvent>()
                val monitorUpSubscriber = TestSubscriber<MonitorUpEvent>()
                val redirectLocation = "/a-relative-path"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)
                val expectedFinalRedirectLocation = URI(monitor.url).resolve(redirectLocation)

                eventDispatcher.subscribeToRedirectEvents { it.toSubscriber(redirectSubscriber) }
                eventDispatcher.subscribeToMonitorUpEvents { it.toSubscriber(monitorUpSubscriber) }
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)
                mockHttpResponse(uptimeCheckerSpy, HttpStatus.OK, expectedFinalRedirectLocation)

                uptimeCheckerSpy.check(monitor)

                then("it should use the original URL as the base for the redirect") {
                    val expectedRedirectEvent = redirectSubscriber.values().first()
                    val expectedUpEvent = monitorUpSubscriber.values().first()

                    redirectSubscriber.awaitCount(1)
                    expectedRedirectEvent.redirectLocation shouldBe expectedFinalRedirectLocation
                    expectedRedirectEvent.monitor.id shouldBe monitor.id

                    monitorUpSubscriber.awaitCount(1)
                    expectedUpEvent.status shouldBe HttpStatus.OK
                    expectedUpEvent.monitor.id shouldBe monitor.id
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
            .status<Any>(httpStatus)
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
        } returns fromArray(response)
    }
}
