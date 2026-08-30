package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.IneligibleStatusCodeException
import com.kuvaszuptime.kuvasz.models.InvalidRedirectionException
import com.kuvaszuptime.kuvasz.models.RedirectLoopException
import com.kuvaszuptime.kuvasz.models.TooManyRedirectsException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HttpResponseStatusCheckerTest : ShouldSpec({

    val mockUptimeRepo = mockk<HttpUptimeEventRepository>(relaxed = true)
    val mockDbEventHandler = mockk<DatabaseEventHandler>(relaxed = true)
    val mockPendingFailureRepo = mockk<PendingFailureRepository>(relaxed = true)
    val dispatcher = EventDispatcher()
    val checker =
        HttpResponseStatusChecker(dispatcher, mockUptimeRepo, mockDbEventHandler, mockPendingFailureRepo, AppConfig())

    fun checkerWithMaxRedirects(maxRedirects: Int) = HttpResponseStatusChecker(
        dispatcher,
        mockUptimeRepo,
        mockDbEventHandler,
        mockPendingFailureRepo,
        AppConfig().apply { httpCheckMaxRedirects = maxRedirects },
    )

    fun mockResponse(mockStatus: HttpStatus, redirectLocation: String? = null): HttpCheckResponse {
        val mockHttpResponse = SimpleHttpResponseFactory()
            .status<ByteBuffer<*>>(mockStatus)
            .headers { headers ->
                if (redirectLocation != null) {
                    headers.add(HttpHeaders.LOCATION, redirectLocation)
                }
            }
        return mockk<HttpCheckResponse>(relaxed = true) {
            every { httpResponse } returns mockHttpResponse
        }
    }

    afterTest { clearMocks(mockDbEventHandler) }

    context("expected status is not explicitly set") {

        should("return Continue for 2xx status") {
            table(
                headers("status"),
                row(HttpStatus.OK),
                row(HttpStatus.CREATED),
                row(HttpStatus.ACCEPTED),
                row(HttpStatus.NON_AUTHORITATIVE_INFORMATION),
                row(HttpStatus.NO_CONTENT),
                row(HttpStatus.RESET_CONTENT),
                row(HttpStatus.PARTIAL_CONTENT),
                row(HttpStatus.MULTI_STATUS),
                row(HttpStatus.ALREADY_IMPORTED),
                row(HttpStatus.IM_USED),
            ).forAll { httpStatus ->
                val upSubscriber = dispatcher.upSubscriber()
                val downSubscriber = dispatcher.downSubscriber()
                val redirectSubscriber = dispatcher.redirectSubscriber()

                val mockMonitor = mockMonitor()
                val response = mockResponse(httpStatus)

                val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

                result shouldBe HttpCheckResult.Continue
                upSubscriber.assertNoValues()
                downSubscriber.assertNoValues()
                redirectSubscriber.assertNoValues()
                verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
            }
        }

        should("return Finished and dispatch a DOWN event for non-2xx status") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor()
            val response = mockResponse(HttpStatus.NOT_FOUND)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<IneligibleStatusCodeException>(
                "Response status code [404] was unexpected"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Finished and dispatch a DOWN event for 3xx status when followRedirects is false") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = false)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<InvalidRedirectionException>(
                "The request was redirected, but following redirects is disabled"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Redirected for 3xx status when followRedirects is true") {
            table(
                headers("status"),
                row(HttpStatus.SEE_OTHER),
                row(HttpStatus.MOVED_PERMANENTLY),
                row(HttpStatus.TEMPORARY_REDIRECT),
                row(HttpStatus.PERMANENT_REDIRECT),
                row(HttpStatus.FOUND),
                row(HttpStatus.NOT_MODIFIED),
            ).forAll { httpStatus ->
                val upSubscriber = dispatcher.upSubscriber()
                val downSubscriber = dispatcher.downSubscriber()
                val redirectSubscriber = dispatcher.redirectSubscriber()

                val mockMonitor = mockMonitor(followRedirects = true)
                val response = mockResponse(httpStatus, "https://else.com/redirect")

                val result = checker.evaluate(
                    HttpResponseCheckContext(
                        monitor = mockMonitor,
                        response = response,
                        visitedUrls = mutableListOf("/something".toUri())
                    )
                )

                redirectSubscriber.assertSingleValue(mockMonitor.id, "https://else.com/redirect")
                result shouldBe HttpCheckResult.Redirected(
                    redirectionUri = "https://else.com/redirect".toUri(),
                    visitedUrls = mutableListOf("/something".toUri())
                )
                upSubscriber.assertNoValues()
                downSubscriber.assertNoValues()
                verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
            }
        }

        should("return Finished and dispatch a DOWN event for 3xx status without Location header") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<InvalidRedirectionException>(
                "Invalid redirection without a Location header"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Finished and dispatch a DOWN event for 3xx status with visited redirection URI") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "/redirect")

            val result = checker.evaluate(
                HttpResponseCheckContext(
                    monitor = mockMonitor,
                    response = response,
                    visitedUrls = mutableListOf("http://example.com/redirect".toUri())
                )
            )

            redirectSubscriber.assertSingleValue(mockMonitor.id, "http://example.com/redirect")
            downSubscriber.assertSingleError<RedirectLoopException>("Redirect loop detected")
            upSubscriber.assertNoValues()
            result shouldBe HttpCheckResult.Finished
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("resolve a relative Location header against the most recently visited hop") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "/somewhere-else?marker=proof")
            val visited = mutableListOf(
                "http://example.com".toUri(),
                "http://intermediate.test/deep/path".toUri(),
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, visited))

            // It must NOT be resolved against the monitor's own URL (http://example.com), otherwise a hop could
            // retarget the request to a host that was never configured
            redirectSubscriber.assertSingleValue(mockMonitor.id, "http://intermediate.test/somewhere-else?marker=proof")
            result shouldBe HttpCheckResult.Redirected(
                redirectionUri = "http://intermediate.test/somewhere-else?marker=proof".toUri(),
                visitedUrls = visited,
            )
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }

        should("resolve a document-relative Location header against the path of the most recently visited hop") {
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.FOUND, "sibling")
            val visited = mutableListOf(
                "http://example.com".toUri(),
                "http://intermediate.test/a/b".toUri(),
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, visited))

            redirectSubscriber.assertSingleValue(mockMonitor.id, "http://intermediate.test/a/sibling")
            result shouldBe HttpCheckResult.Redirected(
                redirectionUri = "http://intermediate.test/a/sibling".toUri(),
                visitedUrls = visited,
            )
            downSubscriber.assertNoValues()
        }

        should("resolve a relative Location header against the monitor's URL when there is no visited hop yet") {
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "/redirect")

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            redirectSubscriber.assertSingleValue(mockMonitor.id, "http://example.com/redirect")
            result shouldBe HttpCheckResult.Redirected(
                redirectionUri = "http://example.com/redirect".toUri(),
                visitedUrls = mutableListOf(),
            )
            downSubscriber.assertNoValues()
        }

        should("return Finished and dispatch a DOWN event when the redirect chain exceeds the configured maximum") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            // Every hop has a distinct URI, so the redirect loop detection would never stop this chain
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "http://attacker.test/4")
            val visited = mutableListOf(
                "http://example.com".toUri(),
                "http://attacker.test/1".toUri(),
                "http://attacker.test/2".toUri(),
            )

            val result = checkerWithMaxRedirects(2)
                .evaluate(HttpResponseCheckContext(mockMonitor, response, visited))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<TooManyRedirectsException>(
                "The redirect chain was longer than the allowed maximum of 2 redirect(s)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Redirected for the last redirect that still fits into the configured maximum") {
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "http://attacker.test/2")
            val visited = mutableListOf(
                "http://example.com".toUri(),
                "http://attacker.test/1".toUri(),
            )

            val result = checkerWithMaxRedirects(2)
                .evaluate(HttpResponseCheckContext(mockMonitor, response, visited))

            redirectSubscriber.assertSingleValue(mockMonitor.id, "http://attacker.test/2")
            result shouldBe HttpCheckResult.Redirected(
                redirectionUri = "http://attacker.test/2".toUri(),
                visitedUrls = visited,
            )
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }

        should("never follow a redirect when the configured maximum is zero") {
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY, "http://attacker.test/1")

            val result = checkerWithMaxRedirects(0).evaluate(
                HttpResponseCheckContext(mockMonitor, response, mutableListOf("http://example.com".toUri()))
            )

            result shouldBe HttpCheckResult.Finished
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<TooManyRedirectsException>(
                "The redirect chain was longer than the allowed maximum of 0 redirect(s)"
            )
        }
    }

    context("expected status is explicitly set") {

        should("return Continue for any of the expected statuses") {
            table(
                headers("statuses"),
                row(listOf(HttpStatus.OK)),
                row(listOf(HttpStatus.ACCEPTED, HttpStatus.NO_CONTENT)),
                row(listOf(HttpStatus.NOT_FOUND, HttpStatus.BAD_REQUEST)),
            ).forAll { expectedStatuses ->
                val upSubscriber = dispatcher.upSubscriber()
                val downSubscriber = dispatcher.downSubscriber()
                val redirectSubscriber = dispatcher.redirectSubscriber()

                val mockMonitor = mockMonitor(expectedStatusCodes = expectedStatuses.map { it.code }.toSet())
                val response = mockResponse(expectedStatuses.random())

                val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

                result shouldBe HttpCheckResult.Continue
                upSubscriber.assertNoValues()
                downSubscriber.assertNoValues()
                redirectSubscriber.assertNoValues()
                verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
            }
        }

        should("return Finished and dispatch a DOWN event for non-expected status") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(expectedStatusCodes = setOf(HttpStatus.OK.code))
            val response = mockResponse(HttpStatus.NO_CONTENT)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<IneligibleStatusCodeException>(
                "Response status code [204] was unexpected"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Finished and dispatch a DOWN event for 3xx status if the status is not expected") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(expectedStatusCodes = setOf(HttpStatus.OK.code), followRedirects = true)
            val response = mockResponse(HttpStatus.MOVED_PERMANENTLY)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<IneligibleStatusCodeException>(
                "Response status code [301] was unexpected"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }

        should("return Redirected for 3xx status if the status is expected") {
            table(
                headers("status"),
                row(HttpStatus.SEE_OTHER),
                row(HttpStatus.MOVED_PERMANENTLY),
                row(HttpStatus.TEMPORARY_REDIRECT),
                row(HttpStatus.PERMANENT_REDIRECT),
                row(HttpStatus.FOUND),
                row(HttpStatus.NOT_MODIFIED),
            ).forAll { httpStatus ->
                val upSubscriber = dispatcher.upSubscriber()
                val downSubscriber = dispatcher.downSubscriber()
                val redirectSubscriber = dispatcher.redirectSubscriber()

                val mockMonitor = mockMonitor(
                    expectedStatusCodes = setOf(httpStatus.code),
                    followRedirects = true
                )
                val response = mockResponse(httpStatus, "/redirect")

                val result = checker.evaluate(
                    HttpResponseCheckContext(
                        monitor = mockMonitor,
                        response = response,
                        visitedUrls = mutableListOf("http://example.com".toUri())
                    )
                )

                redirectSubscriber.assertSingleValue(mockMonitor.id, "http://example.com/redirect")
                upSubscriber.assertNoValues()
                downSubscriber.assertNoValues()
                result shouldBe HttpCheckResult.Redirected(
                    redirectionUri = "http://example.com/redirect".toUri(),
                    visitedUrls = mutableListOf("http://example.com".toUri())
                )
                verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
            }
        }
    }
})
