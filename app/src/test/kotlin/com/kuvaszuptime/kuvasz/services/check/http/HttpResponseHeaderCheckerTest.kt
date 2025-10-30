package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.ExpectedHeaderNotFoundException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.micronaut.http.simple.SimpleHttpResponseFactory
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HttpResponseHeaderCheckerTest : ShouldSpec({

    val mockUptimeRepo = mockk<HttpUptimeEventRepository>(relaxed = true)
    val mockDbEventHandler = mockk<DatabaseEventHandler>(relaxed = true)
    val dispatcher = EventDispatcher()
    val checker = HttpResponseHeaderChecker(dispatcher, mockUptimeRepo, mockDbEventHandler)

    fun mockResponse(mockHeaders: Map<String, String>): HttpCheckResponse {
        val mockHttpResponse = SimpleHttpResponseFactory()
            .status<ByteBuffer<*>>(HttpStatus.OK)
            .headers { headers ->
                // Provide one default header to make sure that the happy path doesn't pass because of missing headers
                headers.add(HttpHeaders.CONTENT_TYPE, "application/json")
                mockHeaders.forEach { mockHeader ->
                    headers.add(mockHeader.key, mockHeader.value)
                }
            }
        return mockk<HttpCheckResponse>(relaxed = true) {
            every { httpResponse } returns mockHttpResponse
        }
    }

    afterTest { clearMocks(mockDbEventHandler) }

    context("expected headers are not set") {

        should("return Continue") {

            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor()
            val response = mockResponse(emptyMap())

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("expected headers are explicitly set") {

        should("return Continue if all expected headers are present - case matches") {

            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(
                expectedHeaders = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    "Content-Type" to "application/json",
                )
            )
            val response = mockResponse(
                mapOf(
                    "X-Custom-Header" to "CustomValue",
                    "Content-Type" to "application/json",
                    "X-Another-Header" to "AnotherValue", // Extra header that is not expected
                )
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }

        should("return Continue if all expected headers are present - case matches - with whitespaces") {

            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(
                expectedHeaders = mapOf(
                    "X-Custom-Header" to "CustomValue ",
                    "Content-Type" to "application/json ",
                )
            )
            val response = mockResponse(
                mapOf(
                    "X-Custom-Header" to "CustomValue ",
                    "Content-Type" to "application/json",
                )
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }

        should("return Continue if all expected headers are present - case is different") {

            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(
                expectedHeaders = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    "CONTENT-TYPE" to "application/json",
                )
            )
            val response = mockResponse(
                mapOf(
                    "x-custom-header" to "CustomValue",
                    "content-type" to "application/json",
                )
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }

        should("return Finished and dispatch a DOWN event for missing expected header") {

            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val redirectSubscriber = dispatcher.redirectSubscriber()

            val mockMonitor = mockMonitor(
                expectedHeaders = mapOf(
                    "X-Custom-Header" to "CustomValue",
                    "Content-Type" to "application/json",
                    "X-Another-Header" to "AnotherValue",
                    "anotherexpected" to "ExpectedValue",
                )
            )
            val response = mockResponse(
                mapOf(
                    "Content-Type" to "application/json",
                    "X-Another-Header" to "AnotherValue",
                    "anotherexpected" to "WrongValue",
                )
            )

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            redirectSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedHeaderNotFoundException>(
                "Response headers did not match the expected headers: [X-Custom-Header, anotherexpected]"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }
})
