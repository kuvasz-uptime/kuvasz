package com.akobor.kuvasz.services

import com.akobor.kuvasz.DatabaseBehaviorSpec
import com.akobor.kuvasz.createMonitor
import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.events.RedirectEvent
import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.util.toUri
import com.akobor.kuvasz.utils.getBean
import com.akobor.kuvasz.utils.startTestApplication
import io.kotest.core.spec.autoClose
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.spyk
import io.reactivex.Flowable.fromArray
import io.reactivex.observers.TestObserver
import java.net.URI

class UptimeCheckerTest : DatabaseBehaviorSpec() {
    init {
        service = autoClose(startTestApplication())
        val uptimeChecker = spyk(service.getBean<UptimeChecker>(), recordPrivateCalls = true)
        val eventDispatcher = service.getBean<EventDispatcher>()
        val monitorRepository = service.getBean<MonitorRepository>()

        given("the UptimeChecker service") {
            `when`("it checks a monitor that is UP") {
                val monitor = createMonitor(monitorRepository)
                val observer = TestObserver<MonitorUpEvent>()
                eventDispatcher.subscribeToMonitorUpEvents(observer)
                mockHttpResponse(uptimeChecker, HttpStatus.OK)

                uptimeChecker.check(monitor)

                then("it should dispatch a MonitorUpEvent") {
                    val expectedEvent = observer.values().first()

                    observer.valueCount() shouldBe 1
                    expectedEvent.status shouldBe HttpStatus.OK
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is DOWN") {
                val monitor = createMonitor(monitorRepository)
                val observer = TestObserver<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorDownEvents(observer)
                mockHttpResponse(uptimeChecker, HttpStatus.INTERNAL_SERVER_ERROR)

                uptimeChecker.check(monitor)

                then("it should dispatch a MonitorDownEvent") {
                    val expectedEvent = observer.values().first()

                    observer.valueCount() shouldBe 1
                    expectedEvent.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected without a Location header") {
                val monitor = createMonitor(monitorRepository)
                val observer = TestObserver<MonitorDownEvent>()
                eventDispatcher.subscribeToMonitorDownEvents(observer)
                mockHttpResponse(uptimeChecker, HttpStatus.PERMANENT_REDIRECT)

                uptimeChecker.check(monitor)

                then("it should dispatch a MonitorDownEvent") {
                    val expectedEvent = observer.values().first()

                    observer.valueCount() shouldBe 1
                    expectedEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected with a Location header, but it's DOWN") {
                val monitor = createMonitor(monitorRepository)
                val redirectObserver = TestObserver<RedirectEvent>()
                val monitorDownObserver = TestObserver<MonitorDownEvent>()
                val redirectLocation = "http://redirected-bad.loc"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)

                eventDispatcher.subscribeToRedirectEvents(redirectObserver)
                eventDispatcher.subscribeToMonitorDownEvents(monitorDownObserver)
                mockHttpResponse(uptimeChecker, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)
                mockHttpResponse(uptimeChecker, HttpStatus.INTERNAL_SERVER_ERROR, redirectLocation.toUri())

                uptimeChecker.check(monitor)

                then("it should dispatch a RedirectEvent and then a MonitorDownEvent") {
                    val expectedRedirectEvent = redirectObserver.values().first()
                    val expectedDownEvent = monitorDownObserver.values().first()

                    redirectObserver.valueCount() shouldBe 1
                    expectedRedirectEvent.redirectLocation shouldBe redirectLocation.toUri()
                    expectedRedirectEvent.monitor.id shouldBe monitor.id

                    monitorDownObserver.valueCount() shouldBe 1
                    expectedDownEvent.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
                    expectedDownEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor that is redirected with a Location header, but it's UP") {
                val monitor = createMonitor(monitorRepository)
                val redirectObserver = TestObserver<RedirectEvent>()
                val monitorUpObserver = TestObserver<MonitorUpEvent>()
                val redirectLocation = "http://redirected-good.loc"
                val headers = mapOf(HttpHeaders.LOCATION to redirectLocation)

                eventDispatcher.subscribeToRedirectEvents(redirectObserver)
                eventDispatcher.subscribeToMonitorUpEvents(monitorUpObserver)
                mockHttpResponse(uptimeChecker, HttpStatus.PERMANENT_REDIRECT, monitor.url.toUri(), headers)
                mockHttpResponse(uptimeChecker, HttpStatus.OK, redirectLocation.toUri())

                uptimeChecker.check(monitor)

                then("it should dispatch a RedirectEvent and then a MonitorUpEvent") {
                    val expectedRedirectEvent = redirectObserver.values().first()
                    val expectedUpEvent = monitorUpObserver.values().first()

                    redirectObserver.valueCount() shouldBe 1
                    expectedRedirectEvent.redirectLocation shouldBe redirectLocation.toUri()
                    expectedRedirectEvent.monitor.id shouldBe monitor.id

                    monitorUpObserver.valueCount() shouldBe 1
                    expectedUpEvent.status shouldBe HttpStatus.OK
                    expectedUpEvent.monitor.id shouldBe monitor.id
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockHttpResponse(
        uptimeChecker: UptimeChecker,
        httpStatus: HttpStatus,
        requestUri: URI? = null,
        additionalHeaders: Map<String, String> = emptyMap()
    ) {
        val response = SimpleHttpResponseFactory()
            .status<Any>(httpStatus)
            .headers { headers ->
                additionalHeaders.forEach { (name, value) ->
                    headers.add(name, value)
                }
            }
        every { uptimeChecker["sendHttpRequest"](requestUri ?: allAny<URI>()) } returns fromArray(response)
    }
}
