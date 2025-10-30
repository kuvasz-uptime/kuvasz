package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.ResponseTimeThresholdExceededException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class HttpResponseTimeCheckerTest : ShouldSpec({

    val mockUptimeRepo = mockk<HttpUptimeEventRepository>(relaxed = true)
    val mockDbEventHandler = mockk<DatabaseEventHandler>(relaxed = true)
    val dispatcher = EventDispatcher()
    val checker = HttpResponseTimeChecker(dispatcher, mockUptimeRepo, mockDbEventHandler)

    fun mockResponse(mockLatency: Int): HttpCheckResponse =
        mockk<HttpCheckResponse>(relaxed = true) {
            every { latency } returns mockLatency
        }

    afterTest { clearMocks(mockDbEventHandler) }

    context("threshold is not set") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()

            val mockMonitor = mockMonitor(responseTimeThreshold = null)
            val response = mockResponse(mockLatency = 1000000)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("response time is within threshold") {

        should("return Continue") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val mockMonitor = mockMonitor(responseTimeThreshold = 2000)
            val response = mockResponse(mockLatency = 1000)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Continue
            upSubscriber.assertNoValues()
            downSubscriber.assertNoValues()
            verify(inverse = true) { mockDbEventHandler.handleUptimeMonitorEvent(any()) }
        }
    }

    context("response time exceeds threshold") {

        should("return Finished and dispatch a DOWN event") {
            val upSubscriber = dispatcher.upSubscriber()
            val downSubscriber = dispatcher.downSubscriber()
            val mockMonitor = mockMonitor(responseTimeThreshold = 2000)
            val response = mockResponse(mockLatency = 3000)

            val result = checker.evaluate(HttpResponseCheckContext(mockMonitor, response, mutableListOf()))

            result shouldBe HttpCheckResult.Finished
            upSubscriber.assertNoValues()
            downSubscriber.assertSingleError<ResponseTimeThresholdExceededException>(
                "Response time exceeded the threshold of 2000 ms (actual: 3000 ms)"
            )
            verify { mockDbEventHandler.handleUptimeMonitorEvent(any<HttpMonitorDownEvent>()) }
        }
    }
})
