package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.jooq.enums.IntegrationThreadSubject
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IntegrationThreadRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import org.jooq.DSLContext
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import org.mockserver.verify.VerificationTimes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * A mass recovery against a rate limited Slack, through the real client (with its retries), and with the recoveries
 * dispatched concurrently, like the checkers of the different monitors do.
 */
class SlackBotBurstTest : ShouldSpec({

    val mockServer = ClientAndServer.startClientAndServer(0)
    // A context of its own, because the API base URI has to point to the dynamic port of the mock server
    val ctx = testAppContext(
        mapOf("internal.slack-api-base-uri" to "http://localhost:${mockServer.port}/api"),
        "slack-bot-burst",
    )
    val dslContext = ctx.getBean<DSLContext>()
    val eventDispatcher = ctx.getBean<EventDispatcher>()
    val databaseEventHandler = ctx.getBean<DatabaseEventHandler>()
    val monitorRepository = ctx.getBean<HttpMonitorRepository>()
    val uptimeEventRepository = ctx.getBean<HttpUptimeEventRepository>()
    val threadRepository = ctx.getBean<IntegrationThreadRepository>()
    val integrationId = IntegrationID(IntegrationType.SLACK_BOT, "burst")

    afterTest { dslContext.resetDatabase() }

    afterSpec { mockServer.stop() }

    fun slackRequest(method: String) = request().withMethod(HttpMethod.POST.name).withPath("/api/$method")

    fun ok() = response()
        .withStatusCode(HttpStatus.OK.code)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody("""{"ok":true,"ts":"$ROOT_TS"}""")

    fun dispatchWithPersistence(event: HttpMonitorDownEvent) {
        databaseEventHandler.handleUptimeMonitorEvent(event)
        eventDispatcher.dispatch(event)
    }

    should("resolve every thread of a mass recovery, even if Slack rate limits the calls") {
        // Opening the threads, without any rate limiting yet
        mockServer.`when`(slackRequest("chat.postMessage")).respond(ok())
        mockServer.`when`(slackRequest("reactions.add")).respond(ok())
        val monitors = (1..BURST_SIZE).map { createHttpMonitor(monitorRepository) }
        val downRecords = monitors.map { monitor ->
            dispatchWithPersistence(HttpMonitorDownEvent(monitor, HttpStatus.BAD_GATEWAY, Exception(), null))
            uptimeEventRepository.fetchByMonitorId(monitor.id).single()
        }
        eventually(TIMEOUT) {
            downRecords.forAll { record ->
                threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, record.id).shouldNotBeNull()
            }
            mockServer.verify(slackRequest("reactions.add"), VerificationTimes.exactly(BURST_SIZE))
        }
        mockServer.reset()

        // Slack refuses the first calls of every method. As many of them, as many retries a single call has, so that
        // even the unluckiest call gets through in the end.
        SLACK_METHODS.forEach { method ->
            mockServer.`when`(slackRequest(method), Times.exactly(RATE_LIMITED_CALLS)).respond(
                response().withStatusCode(HttpStatus.TOO_MANY_REQUESTS.code).withHeader("Retry-After", "1")
            )
            mockServer.`when`(slackRequest(method)).respond(ok())
        }

        // Every monitor recovers at the very same time, on threads of their own
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(BURST_SIZE)
        monitors.zip(downRecords).forEach { (monitor, downRecord) ->
            executor.submit {
                start.await()
                val event = HttpMonitorUpEvent(monitor, HttpStatus.OK, 100, downRecord)
                databaseEventHandler.handleUptimeMonitorEvent(event)
                eventDispatcher.dispatch(event)
            }
        }
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(TIMEOUT.inWholeSeconds, TimeUnit.SECONDS) shouldBe true

        // Every call succeeded in the end: one successful request each, plus the refused ones
        eventually(TIMEOUT) {
            SLACK_METHODS.forEach { method ->
                mockServer.verify(slackRequest(method), VerificationTimes.exactly(BURST_SIZE + RATE_LIMITED_CALLS))
            }
        }
        // Every reply went into its thread
        mockServer.verify(
            slackRequest("chat.postMessage").withBody(JsonBody.json("""{"thread_ts": "$ROOT_TS"}""")),
            VerificationTimes.exactly(BURST_SIZE + RATE_LIMITED_CALLS),
        )
        downRecords.forAll { record ->
            threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, record.id).shouldNotBeNull()
        }
    }
}) {
    companion object {
        private const val ROOT_TS = "1700000000.000100"
        private const val BURST_SIZE = 30
        private const val RATE_LIMITED_CALLS = 5
        private val TIMEOUT = 30.seconds
        private val SLACK_METHODS = listOf("chat.postMessage", "chat.update", "reactions.add", "reactions.remove")
    }
}
