package com.kuvaszuptime.kuvasz.handlers

import arrow.core.Option
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.handlers.SlackEventHandlerConfig
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.SlackWebhookMessage
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SlackWebhookService
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.string.shouldContain
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.reactivex.Flowable

@MicronautTest
class SlackEventHandlerTest(
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository

) : DatabaseBehaviorSpec() {
    private val mockHttpClient = mockk<RxHttpClient>()

    init {
        val eventHandlerConfig = SlackEventHandlerConfig().apply { webhookUrl = "https://jklfdalda.com/webhook" }
        val slackWebhookService = SlackWebhookService(eventHandlerConfig, mockHttpClient)
        val webhookServiceSpy = spyk(slackWebhookService, recordPrivateCalls = true)
        SlackEventHandler(webhookServiceSpy, eventDispatcher)

        given("the SlackEventHandler") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<SlackWebhookMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is UP (200)"
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<SlackWebhookMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is DOWN"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<SlackWebhookMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "Latency: 1000ms"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("First error"),
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("Second error"),
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<SlackWebhookMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "First error"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.empty(),
                    error = Throwable()
                )
                mockHttpResponse(HttpStatus.OK)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<SlackWebhookMessage>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0].text shouldContain "is DOWN (500)"
                    notificationsSent[1].text shouldContain "Latency: 1000ms"
                    notificationsSent[1].text shouldContain "is UP (200)"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.just(firstUptimeRecord),
                    error = Throwable()
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<SlackWebhookMessage>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0].text shouldContain "Latency: 1000ms"
                    notificationsSent[0].text shouldContain "is UP (200)"
                    notificationsSent[1].text shouldContain "is DOWN (500)"
                }
            }

            `when`("it receives an event but an error happens when it calls the webhook") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpErrorResponse(HttpStatus.BAD_REQUEST, "bad_request")

                then("it should send a webhook message about the event") {
                    val slot = slot<SlackWebhookMessage>()

                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is UP (200)"

                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockHttpResponse(status: HttpStatus, body: String = "") {
        every {
            mockHttpClient.exchange<SlackWebhookMessage, String, String>(
                any(),
                Argument.STRING,
                Argument.STRING
            )
        } returns Flowable.just(
            HttpResponse.status<String>(status).body(body)
        )
    }

    private fun mockHttpErrorResponse(status: HttpStatus, body: String = "") {
        every {
            mockHttpClient.exchange<SlackWebhookMessage, String, String>(
                any(),
                Argument.STRING,
                Argument.STRING
            )
        } returns Flowable.error(
            HttpClientResponseException("error", HttpResponse.status<String>(status).body(body))
        )
    }
}
