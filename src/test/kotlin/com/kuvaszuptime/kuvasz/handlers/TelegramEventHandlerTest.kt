package com.kuvaszuptime.kuvasz.handlers

import arrow.core.Option
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.handlers.TelegramEventHandlerConfig
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.TelegramAPIMessage
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TelegramAPIService
import com.kuvaszuptime.kuvasz.tables.UptimeEvent
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
class TelegramEventHandlerTest(
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository
) : DatabaseBehaviorSpec() {
    private val mockHttpClient = mockk<RxHttpClient>()

    init {
        val eventHandlerConfig = TelegramEventHandlerConfig().apply {
            token = "my_token"
            chatId = "@channel"
        }
        val telegramAPIService = TelegramAPIService(eventHandlerConfig, mockHttpClient)
        val apiServiceSpy = spyk(telegramAPIService, recordPrivateCalls = true)
        TelegramEventHandler(apiServiceSpy, eventHandlerConfig, eventDispatcher)

        given("the TelegramEventHandler") {
            `when`("it receives a MonitorUpEvent and There is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpResponse(HttpStatus.OK)

                eventDispatcher.dispatch(event)

                then("it should send a message about the event") {
                    val slot = slot<TelegramAPIMessage>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
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

                then("it should send a message about the event") {
                    val slot = slot<TelegramAPIMessage>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
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
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UptimeEvent.UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<TelegramAPIMessage>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
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
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UptimeEvent.UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Throwable("Second error"),
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<TelegramAPIMessage>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured.text shouldContain "(500)"
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
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UptimeEvent.UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<TelegramAPIMessage>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
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
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UptimeEvent.UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.just(firstUptimeRecord),
                    error = Throwable()
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<TelegramAPIMessage>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0].text shouldContain "Latency: 1000ms"
                    notificationsSent[0].text shouldContain "is UP (200)"
                    notificationsSent[1].text shouldContain "is DOWN (500)"
                }
            }

            `when`("it receives an event but an error happens when it calls the API") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpErrorResponse(HttpStatus.BAD_REQUEST, "bad_request")

                then("it should send a message about the event") {
                    val slot = slot<TelegramAPIMessage>()

                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
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
            mockHttpClient.exchange<TelegramAPIMessage, String, String>(
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
            mockHttpClient.exchange<TelegramAPIMessage, String, String>(
                any(),
                Argument.STRING,
                Argument.STRING
            )
        } returns Flowable.error(
            HttpClientResponseException("error", HttpResponse.status<String>(status).body(body))
        )
    }
}
