package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckRequestConfigurator
import com.kuvaszuptime.kuvasz.services.check.http.HttpUptimeChecker
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBeUriOf
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpError
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.NottableString.not
import org.mockserver.model.NottableString.string
import org.mockserver.verify.VerificationTimes
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = false)
class UptimeCheckerE2ETest(
    uptimeChecker: HttpUptimeChecker,
    private val monitorRepository: HttpMonitorRepository,
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
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that returns a client error, but it's expected") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedStatusCodes = setOf(HttpStatus.NOT_FOUND.code)
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.NOT_FOUND.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.NOT_FOUND
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
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

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
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should use the right Cache-Control header") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

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
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
                .withHeader(not("Cache-Control"), string(".*"))
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should not use the Cache-Control header") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

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
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedRedirectEvents = redirectSubscriber.awaitCount(2).values()
                val expectedUpEvent = upSubscriber.awaitCount(1).values().first()

                downSubscriber.assertNoValues()

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

        `when`("it checks a monitor that is redirected - following redirects is enabled, explicit status codes") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
                expectedStatusCodes = setOf(200, 307, 308)
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedRedirectEvents = redirectSubscriber.awaitCount(2).values()
                val expectedUpEvent = upSubscriber.awaitCount(1).values().first()

                downSubscriber.assertNoValues()

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

        `when`(
            "it checks a monitor that is redirected - following redirects is enabled, " +
                "but the returned redirect status code is not expected"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
                expectedStatusCodes = setOf(200, 201, 301)
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )

            uptimeChecker.check(monitor)

            then("it should not follow the redirect and dispatch a DOWN event") {
                redirectSubscriber.assertNoValues()
                upSubscriber.assertNoValues()

                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()

                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                expectedDownEvent.error.message shouldStartWith
                    "Response status code [308] was unexpected"

                mockServer.verifyRequest(request1)
            }
        }

        `when`(
            "it checks a monitor that is redirected - following redirects is enabled - " +
                "final status code is not acceptable"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
                expectedStatusCodes = setOf(201, 308)
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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

            then("it should follow the redirect and should dispatch a MonitorDownEvent") {
                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()
                val expectedRedirectEvent = redirectSubscriber.awaitCount(1).values().first()

                upSubscriber.assertNoValues()

                expectedRedirectEvent.monitor.id shouldBe monitor.id
                expectedRedirectEvent.redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"

                expectedDownEvent.status shouldBe HttpStatus.OK
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe "Response status code [200] was unexpected"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2)
            }
        }

        `when`("it checks a monitor that is redirected - following redirects is enabled - relative redirect") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedRedirectEvents = redirectSubscriber.awaitCount(2).values()
                val expectedUpEvent = upSubscriber.awaitCount(1).values().first()

                downSubscriber.assertNoValues()

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
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()

                redirectSubscriber.assertNoValues()
                upSubscriber.assertNoValues()

                expectedDownEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe
                    "The request was redirected, but following redirects is disabled"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2, exactly = 0)
            }
        }

        `when`(
            "it checks a monitor that is redirected - following redirects is disabled, " +
                "but the returned status code is explicitly accepted"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = false,
                expectedStatusCodes = setOf(HttpStatus.PERMANENT_REDIRECT.code)
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
                    .withHeader(HttpHeaders.LOCATION, "$mockServerUrl/redirected-path1")
            )

            uptimeChecker.check(monitor)

            then("it should not follow the redirects and should dispatch a MonitorUpEvent") {
                redirectSubscriber.assertNoValues()
                downSubscriber.assertNoValues()

                val expectedUpEvent = upSubscriber.awaitCount(1).values().first()

                expectedUpEvent.status shouldBe HttpStatus.PERMANENT_REDIRECT
                expectedUpEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request1)
            }
        }

        `when`("it checks a monitor that is redirected - following redirect enabled - but no Location header") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                followRedirects = true,
            )
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request1 = getRequest("/some-path")

            mockServer.`when`(request1).respond(
                response()
                    .withStatusCode(HttpStatus.PERMANENT_REDIRECT.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()

                redirectSubscriber.assertNoValues()
                upSubscriber.assertNoValues()

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
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()
                val expectedRedirectEvent = redirectSubscriber.awaitCount(1).values().first()

                upSubscriber.assertNoValues()

                expectedRedirectEvent.monitor.id shouldBe monitor.id
                expectedRedirectEvent.redirectLocation shouldBeUriOf "$mockServerUrl/redirected-path1"

                expectedDownEvent.status shouldBe HttpStatus.BAD_GATEWAY
                expectedDownEvent.monitor.id shouldBe monitor.id
                expectedDownEvent.error.message shouldBe "Bad Gateway"

                mockServer.verifyRequest(request1)
                mockServer.verifyRequest(request2, exactly = 3)
            }
        }

        `when`("it checks a monitor that is DOWN - valid client-related HTTP status code") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.NOT_ACCEPTABLE.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.NOT_ACCEPTABLE
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Response status code [406] was unexpected"

                mockServer.verifyRequest(request, exactly = 1)
            }
        }

        `when`("it checks a monitor that is DOWN - valid server-related HTTP status code") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.BAD_GATEWAY.code)
            )

            uptimeChecker.check(monitor)

            then("it should retry the request 3 times before it dispatches a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.BAD_GATEWAY
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Bad Gateway"

                mockServer.verifyRequest(request, exactly = 3)
            }
        }

        `when`("it checks a monitor that drops the connection") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).error(HttpError().withDropConnection(true))

            uptimeChecker.check(monitor)

            then("it should retry the check 3 times in total before it dispatches a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                mockServer.verifyRequest(request, exactly = 3)

                expectedEvent.status.shouldBeNull()
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Connection closed before response was received"
            }
        }

        `when`("it checks a monitor that is unreachable") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "https://34hkl2jklvd.com/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            uptimeChecker.check(monitor)

            then("it should handle the connection the right way") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status.shouldBeNull()
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Connect Error: 34hkl2jklvd.com"
            }
        }

        `when`("it checks a monitor that is DOWN - invalid HTTP status code") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.HEAD,
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = headRequest("/some-path")
            mockServer.`when`(request).respond(
                response().withStatusCode(489)
            )

            uptimeChecker.check(monitor)

            then("it should handle the invalid status code gracefully") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe null
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Invalid HTTP status code: 489"

                mockServer.verifyRequest(request, exactly = 1)
            }
        }

        `when`("it checks a monitor and it's done with it") {

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            var doAfterCalledWithMonitorId: Long = -1

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
            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            val redirectSubscriber = TestSubscriber<HttpRedirectEvent>()
            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
            eventDispatcher.subscribeToHttpRedirectEvents { it.forwardToSubscriber(redirectSubscriber) }
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

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
                val expectedRedirectEvents = redirectSubscriber.awaitCount(3).values()
                val expectedDownEvent = downSubscriber.awaitCount(1).values().first()

                upSubscriber.assertNoValues()

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

        `when`("it checks a monitor that is UP - the response time exceeds the threshold") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                responseTimeThresholdMillis = 1000
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withDelay(org.mockserver.model.Delay.delay(TimeUnit.MILLISECONDS, 1005))
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldStartWith
                    "Response time exceeded the threshold of 1000 ms"

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the response time is below the threshold") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                responseTimeThresholdMillis = 1000
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withDelay(org.mockserver.model.Delay.delay(TimeUnit.MILLISECONDS, 500))
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the expected keyword is not found in the response") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "darkness"
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe
                    "Response body does not contain the expected keyword: darkness (case-insensitive)"

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the expected keyword is found in the response") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "lo, w"
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the response body is empty") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "darkness"
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Response body is empty or not a string"

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the response body is null") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "darkness"
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe "Response body is empty or not a string"

                mockServer.verifyRequest(request)
            }
        }

        `when`(
            "it checks a monitor that is UP - " +
                "the expected keyword is not found in the response - case sensitive"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "Hello, world!",
                expectedKeywordCaseSensitive = true
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe
                    "Response body does not contain the expected keyword: Hello, world! (case-sensitive)"

                mockServer.verifyRequest(request)
            }
        }

        `when`(
            "it checks a monitor that is UP - " +
                "the expected keyword is found in the response - case insensitive"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "Hello, world!",
                expectedKeywordCaseSensitive = false
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the expected keyword is not found in the response - negated") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "darkness",
                expectedKeywordNegated = true
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the expected keyword is found in the response - negated") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "Hello, world!",
                expectedKeywordNegated = true
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe
                    "Response body should not contain the expected keyword: Hello, world! (case-insensitive)"

                mockServer.verifyRequest(request)
            }
        }

        `when`(
            "it checks a monitor that is UP - " +
                "the expected keyword is not found in the response - negated and case sensitive"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "Hello, world!",
                expectedKeywordNegated = true,
                expectedKeywordCaseSensitive = true
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`(
            "it checks a monitor that is UP - " +
                "the expected keyword is found in the response - negated and case sensitive"
        ) {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = "Hello, world!",
                expectedKeywordNegated = true,
                expectedKeywordCaseSensitive = true
            )
            val subscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withBody("Hello, world!")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe
                    "Response body should not contain the expected keyword: Hello, world! (case-sensitive)"

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor that is UP - the expected keyword is an empty string") {
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedKeyword = ""
            )
            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val request = getRequest("/some-path")
            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
            )

            uptimeChecker.check(monitor)

            then("it should not check the body, but dispatch a MonitorUpEvent") {
                val expectedEvent = subscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor with overriding the built-in headers") {

            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                requestHeaders = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    HttpHeaders.USER_AGENT to "CustomUserAgent/1.0",
                    HttpHeaders.ACCEPT to "application/json",
                    HttpHeaders.ACCEPT_ENCODING to "gzip",
                    HttpHeaders.HOST to "example.com",
                    HttpHeaders.CACHE_CONTROL to "must-revalidate, no-cache"
                )
            )

            val request = request()
                .withMethod(HttpMethod.GET.literal)
                .withPath("/some-path")
                .withHeader("X-Custom-Header", "CustomValue")
                .withHeader(HttpHeaders.USER_AGENT, "CustomUserAgent/1.0")
                .withHeader(HttpHeaders.ACCEPT, "application/json")
                .withHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .withHeader(HttpHeaders.HOST, "example.com")
                .withHeader(HttpHeaders.CACHE_CONTROL, "must-revalidate, no-cache")

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.NO_CONTENT.code)
            )

            uptimeChecker.check(monitor)

            then("it should really override them in the request") {

                subscriber.awaitCount(1)
                mockServer.verify(
                    request,
                    VerificationTimes.exactly(1)
                )
            }
        }

        `when`("it checks a monitor with a custom request body") {

            val subscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(subscriber) }
            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.POST,
                requestBody = """{"key": "value"}"""
            )

            val request = request()
                .withMethod(monitor.requestMethod.literal)
                .withPath("/some-path")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(monitor.requestBody)

            mockServer.`when`(request).respond(
                response().withStatusCode(HttpStatus.CREATED.code)
            )

            uptimeChecker.check(monitor)

            then("it should send the request with the custom body") {

                subscriber.awaitCount(1)
                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor with explicitly set expected headers - they are matching") {

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedHeaders = mapOf(
                    HttpHeaders.CONTENT_TYPE to MediaType.APPLICATION_JSON,
                    "X-Custom-Header" to "CustomValue"
                )
            )

            val upSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

            val request = getRequest("/some-path")

            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .withHeader("x-custom-header", "CustomValue ")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorUpEvent") {
                val expectedEvent = upSubscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }

        `when`("it checks a monitor with explicitly set expected headers - they are not matching") {

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
                expectedHeaders = mapOf(
                    HttpHeaders.CONTENT_TYPE to MediaType.APPLICATION_JSON,
                    "X-Custom-Header" to "CustomValue",
                    "X-Another-Header" to "AnotherValue",
                )
            )

            val downSubscriber = TestSubscriber<HttpMonitorDownEvent>()
            eventDispatcher.subscribeToHttpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

            val request = getRequest("/some-path")

            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .withHeader("X-Custom-Header", "WrongValue")
            )

            uptimeChecker.check(monitor)

            then("it should dispatch a MonitorDownEvent") {
                val expectedEvent = downSubscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id
                expectedEvent.error.message shouldBe
                    "Response headers did not match the expected headers: [X-Custom-Header, X-Another-Header]"

                mockServer.verifyRequest(request)
            }
        }

        // The default max header size in Netty is 8192 bytes, which can cause issues in rare cases where the server
        // responds with really large headers. This test is meant to verify that the max header size is properly
        // configured
        `when`("it checks a monitor with headers larger than 8192 bytes") {

            val monitor = createMonitor(
                repository = monitorRepository,
                url = "$mockServerUrl/some-path",
                requestMethod = HttpMethod.GET,
            )

            val testSubscriber = TestSubscriber<HttpMonitorUpEvent>()
            eventDispatcher.subscribeToHttpMonitorUpEvents { it.forwardToSubscriber(testSubscriber) }

            val request = getRequest("/some-path")

            mockServer.`when`(request).respond(
                response()
                    .withStatusCode(HttpStatus.OK.code)
                    .withHeader("A-Really-Large-One", "a".repeat(12000))
            )

            uptimeChecker.check(monitor)

            then("it should dispatch an UP event instead of failing and dispatching a DOWN event") {
                val expectedEvent = testSubscriber.awaitCount(1).values().first()

                expectedEvent.status shouldBe HttpStatus.OK
                expectedEvent.monitor.id shouldBe monitor.id

                mockServer.verifyRequest(request)
            }
        }
    }
})

private fun getRequest(path: String) =
    request()
        .withMethod(HttpMethod.GET.literal)
        .withPath(path)
        .withHeader(HttpHeaders.USER_AGENT, HttpCheckRequestConfigurator.USER_AGENT)

private fun headRequest(path: String) =
    request()
        .withMethod(HttpMethod.HEAD.literal)
        .withPath(path)
        .withHeader(HttpHeaders.USER_AGENT, HttpCheckRequestConfigurator.USER_AGENT)

private fun ClientAndServer.verifyRequest(request: HttpRequest, exactly: Int = 1) =
    verify(
        request
            .withHeader(HttpHeaders.USER_AGENT, HttpCheckRequestConfigurator.USER_AGENT),
        if (exactly == 0) VerificationTimes.never() else VerificationTimes.exactly(exactly)
    )
