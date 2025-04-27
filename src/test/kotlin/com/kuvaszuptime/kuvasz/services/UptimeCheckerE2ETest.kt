package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBeUriOf
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.not
import org.mockserver.model.NottableString.string
import org.mockserver.verify.VerificationTimes

@MicronautTest(startApplication = false)
class UptimeCheckerE2ETest(
    uptimeChecker: UptimeChecker,
    private val monitorRepository: MonitorRepository,
    private val eventDispatcher: EventDispatcher
) : DatabaseBehaviorSpec({

    lateinit var mockServer: ClientAndServer
    val mockServerUrl = "http://localhost:1080"

    beforeSpec {
        mockServer = ClientAndServer.startClientAndServer(1080)
    }

    afterSpec {
        mockServer.stop()
    }

    afterContainer { mockServer.reset() }

    given("the UptimeChecker service") {

        `when`("it checks a monitor that is UP - GET") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
            )
            val subscriber = TestSubscriber<MonitorUpEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - HEAD") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<MonitorUpEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor - forceNoCache is true") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
                forceNoCache = true,
            )
            val subscriber = TestSubscriber<MonitorUpEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should use the right Cache-Control header") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor - forceNoCache is false") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
                forceNoCache = false,
            )
            val subscriber = TestSubscriber<MonitorUpEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
                .withHeader(not("Cache-Control"), string(".*"))
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should not use the Cache-Control header") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
            }
        }

        `when`("it checks a monitor that is redirected - following redirects is enabled") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")
            val request2 = getRequest("/redirected-path1")
            val request3 = getRequest("/redirected-path2")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )
            mockServer.`when`(request2).respond(
                response()
                    .withStatusCode(HttpStatus.TEMPORARY_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path2")
            )
            mockServer.`when`(request3).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should follow the redirects") {
                val expectedRedirectEvents = redirectSubscriber.values()
                val expectedUpEvent = upSubscriber.values().first()

                redirectSubscriber.awaitCount(2)
                upSubscriber.awaitCount(1)
                downSubscriber.assertValueCount(0)

                expectedUpEvent.status shouldBe HttpStatus.OK
                expectedUpEvent.monitor.id shouldBe monitor.id

                expectedRedirectEvents.forAll { it.monitor.id shouldBe monitor.id }
                expectedRedirectEvents[0].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"
                expectedRedirectEvents[1].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path2"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2)
                mockServer.verifyRequest(request3)
            }
        }

        `when`("it checks a monitor that is redirected - following redirects is enabled - relative redirect") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")
            val request2 = getRequest("/redirected-path1")
            val request3 = getRequest("/redirected-path2")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "/redirected-path1")
            )
            mockServer.`when`(request2).respond(
                response()
                    .withStatusCode(HttpStatus.TEMPORARY_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "/redirected-path2")
            )
            mockServer.`when`(request3).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should follow the redirects") {
                val expectedRedirectEvents = redirectSubscriber.values()
                val expectedUpEvent = upSubscriber.values().first()

                redirectSubscriber.awaitCount(2)
                upSubscriber.awaitCount(1)
                downSubscriber.assertValueCount(0)

                expectedUpEvent.status shouldBe HttpStatus.OK
                expectedUpEvent.monitor.id shouldBe monitor.id

                expectedRedirectEvents.forAll { it.monitor.id shouldBe monitor.id }
                expectedRedirectEvents[0].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"
                expectedRedirectEvents[1].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path2"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2)
                mockServer.verifyRequest(request3)
            }
        }

        `when`("it checks a monitor that is redirected - following redirects is disabled") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = false,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")
            val request2 = getRequest("/redirected-path1")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )
            mockServer.`when`(request2).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should not follow the redirects and should dispatch a MonitorDownEvent") {
                val expectedDownEvent = downSubscriber.values().first()

                redirectSubscriber.assertValueCount(0)
                upSubscriber.assertValueCount(0)
                downSubscriber.awaitCount(1)

                expectedDownEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe
                    "The request was redirected, but the followRedirects option is disabled"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2, exactly = 0)
            }
        }

        `when`("it checks a monitor that is redirected - following redirect enabled - but no Location header") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedDownEvent = downSubscriber.values().first()

                redirectSubscriber.assertValueCount(0)
                upSubscriber.assertValueCount(0)
                downSubscriber.awaitCount(1)

                expectedDownEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe "Invalid redirection without a Location header"

                mockServer.verifyRequest(request1)
            }
        }

        `when`("it checks a monitor that is redirected - following redirects is enabled - target is down") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")
            val request2 = getRequest("/redirected-path1")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )
            mockServer.`when`(request2).respond(
                response()
                    .withStatusCode(HttpStatus.BAD_GATEWAY.code)
            )

            uptimeChecker.check(monitor)

            then("it should follow the redirect and should dispatch a MonitorDownEvent") {
                val expectedDownEvent = downSubscriber.values().first()
                val expectedRedirectEvent = redirectSubscriber.values().first()

                redirectSubscriber.awaitCount(1)
                downSubscriber.awaitCount(1)
                upSubscriber.assertValueCount(0)

                expectedRedirectEvent.monitor.id shouldBe monitor.id
                expectedRedirectEvent.redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"

                expectedDownEvent.status shouldBe HttpStatus.BAD_GATEWAY
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe "Bad Gateway"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2, exactly = 3)
            }
        }

        `when`("it checks a monitor that is DOWN - valid HTTP status code") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.NOT_ACCEPTABLE.code)
            )

            uptimeChecker.check(monitor)

            then("it should retry the check 3 times in total before it dispatches a MonitorDownEvent") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe HttpStatus.NOT_ACCEPTABLE
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Not Acceptable"

                mockServer.verifyRequest(request, exactly = 3)
            }
        }

        `when`("it checks a monitor that is DOWN - invalid HTTP status code") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(489)
            )

            uptimeChecker.check(monitor)

            then("it should handle the invalid status code gracefully") {
                val expectedEvent = subscriber.values().first()

                subscriber.awaitCount(1)
                expectedEvent.status shouldBe null
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Invalid HTTP status code: 489"

                mockServer.verifyRequest(request, exactly = 3)
            }
        }

        `when`("it checks a monitor and it's done with it") {

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
            )
            val subscriber = TestSubscriber<MonitorUpEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            var doAfterCalledWithMonitorId: Int = -1

            uptimeChecker.check(monitor) { doAfterCalledWithMonitorId = it.id }

            then("it should invoke the doAfter() hook") {
                subscriber.awaitCount(1)

                doAfterCalledWithMonitorId shouldBe monitor.id
            }
        }

        `when`("it checks a monitor that is redirected - but there is a redirect loop") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<MonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<RedirectEvent>()
            val downSubscriber = TestSubscriber<MonitorDownEvent>()
            eventDispatcher.subscribeToMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")
            val request2 = getRequest("/redirected-path1")
            val request3 = getRequest("/redirected-path2")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )
            mockServer.`when`(request2).respond(
                response()
                    .withStatusCode(HttpStatus.TEMPORARY_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path2")
            )
            mockServer.`when`(request3).respond(
                response()
                    .withStatusCode(HttpStatus.TEMPORARY_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/some-path")
            )

            uptimeChecker.check(monitor)

            then("it should break the redirect loop and dispatch a MonitorDownEvent") {
                val expectedRedirectEvents = redirectSubscriber.values()
                val expectedDownEvent = downSubscriber.values().first()

                redirectSubscriber.awaitCount(3)
                upSubscriber.assertValueCount(0)
                downSubscriber.awaitCount(1)

                expectedDownEvent.status shouldBe HttpStatus.TEMPORARY_REDIRECT
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe "Redirect loop detected"

                expectedRedirectEvents.forAll { it.monitor.id shouldBe monitor.id }
                expectedRedirectEvents[0].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"
                expectedRedirectEvents[1].redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path2"
                expectedRedirectEvents[2].redirectLocation shouldBeUriOf "$mockServerUrl/some-path"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2)
                mockServer.verifyRequest(request3)
            }
        }
    }
})

private fun getRequest(path: String) =
    request()
        .withMethod(HttpMethod.GET.literal)
        .withPath(path)
        .withHeader(HttpHeaders.USER_AGENT, UptimeChecker.USER_AGENT)

private fun headRequest(path: String) =
    request()
        .withMethod(HttpMethod.HEAD.literal)
        .withPath(path)
        .withHeader(HttpHeaders.USER_AGENT, UptimeChecker.USER_AGENT)

private fun ClientAndServer.verifyRequest(request: HttpRequest, exactly: Int = 1) =
    verify(
        request
            .withHeader(HttpHeaders.USER_AGENT, UptimeChecker.USER_AGENT),
        if (exactly == 0) VerificationTimes.never() else VerificationTimes.exactly(exactly)
    )
