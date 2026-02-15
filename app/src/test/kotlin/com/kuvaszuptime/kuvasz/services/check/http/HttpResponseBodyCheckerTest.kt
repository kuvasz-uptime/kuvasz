package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.ExpectedKeywordNotFoundException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HttpResponseBodyCheckerTest : ShouldSpec({

    val mockUptimeRepo = mockk<HttpUptimeEventRepository>(relaxed = true)
    val mockDbEventHandler = mockk<DatabaseEventHandler>()
    val dispatcher = EventDispatcher()
    val checker = HttpResponseBodyChecker(dispatcher, mockUptimeRepo, mockDbEventHandler)

    fun mockResponseBody(body: String?): HttpCheckResponse =
        mockk<HttpCheckResponse>(relaxed = true) {
            if (body != null) {
                every { httpResponse.getBodyAs<String>() } returns body
            }
        }

    beforeTest {
        every {
            mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>())
        } returns HttpUptimeEventRecord().apply {
            failureCount = 1
        }
        every {
            mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorUpEvent>())
        } returns HttpUptimeEventRecord().apply {
            failureCount = 0
        }
    }

    afterTest { clearMocks(mockDbEventHandler) }

    context("keyword is found in the response - case-insensitive, non-negated") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test keyword response")
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("keyword is found in the response - case-sensitive, non-negated") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test Keyword response")
            val monitor = mockMonitor(
                expectedKeyword = "Keyword",
                expectedKeywordCaseSensitive = true,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("keyword is found in the response - case-insensitive, negated") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test KEYWorD response")
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = true
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Response body should not contain the expected keyword: keyword (case-insensitive)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("keyword is found in the response - case-sensitive, negated") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test Keyword response")
            val monitor = mockMonitor(
                expectedKeyword = "Keyword",
                expectedKeywordCaseSensitive = true,
                expectedKeywordNegated = true
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Response body should not contain the expected keyword: Keyword (case-sensitive)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("keyword is not found in the response - case-insensitive, non-negated") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test response")
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Response body does not contain the expected keyword: keyword (case-insensitive)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("keyword is not found in the response - case-sensitive, non-negated") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a keyword test response")
            val monitor = mockMonitor(
                expectedKeyword = "Keyword",
                expectedKeywordCaseSensitive = true,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Response body does not contain the expected keyword: Keyword (case-sensitive)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("keyword is not found in the response - case-insensitive, negated") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test keywor response")
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = true
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("keyword is not found in the response - case-sensitive, negated") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("This is a test KEYWORd response")
            val monitor = mockMonitor(
                expectedKeyword = "KEYWORD",
                expectedKeywordCaseSensitive = true,
                expectedKeywordNegated = true
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("response body is null") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody(null)
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Error while checking response body for monitor with ID: 1, expected keyword: keyword, error:"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("response body is empty string") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody("")
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Response body does not contain the expected keyword: keyword (case-insensitive)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("response body throws an exception when getting body") {

        should("dispatch down event and return Finished") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockk<HttpCheckResponse>(relaxed = true) {
                every { httpResponse.getBodyAs<String>() } throws RuntimeException("Error getting body")
            }
            val monitor = mockMonitor(
                expectedKeyword = "keyword",
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ExpectedKeywordNotFoundException>(
                "Error while checking response body for monitor with ID: ${monitor.id}, " +
                    "expected keyword: keyword, error: Error getting body"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }

    context("monitor has no expected keyword") {

        should("return Continue without checking response body") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val response = mockResponseBody(null)
            val monitor = mockMonitor(
                expectedKeyword = null,
                expectedKeywordCaseSensitive = false,
                expectedKeywordNegated = false
            )
            val ctx = HttpResponseCheckContext(monitor, response, mutableListOf())

            val result = checker.evaluate(ctx)

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }
})
