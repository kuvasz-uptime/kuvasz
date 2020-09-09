package com.kuvaszuptime.kuvasz.handlers

import arrow.core.Option
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.handlers.TelegramEventHandlerConfig
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.SlackWebhookMessage
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TelegramAPIService
import com.kuvaszuptime.kuvasz.tables.SslEvent
import com.kuvaszuptime.kuvasz.tables.UptimeEvent
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import java.time.OffsetDateTime

@MicronautTest
class TelegramEventHandlerTest(
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository
) : DatabaseBehaviorSpec() {
    private val mockHttpClient = mockk<RxHttpClient>()

    init {
        val eventHandlerConfig = TelegramEventHandlerConfig().apply {
            token = "my_token"
            chatId = "@channel"
        }
        val telegramAPIService = TelegramAPIService(eventHandlerConfig, mockHttpClient)
        val apiServiceSpy = spyk(telegramAPIService, recordPrivateCalls = true)
        TelegramEventHandler(apiServiceSpy, eventDispatcher)

        given("the TelegramEventHandler") {
            `when`("it receives a MonitorUpEvent and There is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is UP (200)"
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
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is DOWN"
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
                mockSuccessfulHttpResponse()
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
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Latency: 1000ms"
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
                mockSuccessfulHttpResponse()
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
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "(500)"
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
                mockSuccessfulHttpResponse()
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
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "is DOWN (500)"
                    notificationsSent[1] shouldContain "Latency: 1000ms"
                    notificationsSent[1] shouldContain "is UP (200)"
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
                mockSuccessfulHttpResponse()
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
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "Latency: 1000ms"
                    notificationsSent[0] shouldContain "is UP (200)"
                    notificationsSent[1] shouldContain "is DOWN (500)"
                }
            }
        }

        given("the TelegramEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                            "Your site \"${monitor.name}\" (${monitor.url}) has a VALID certificate"
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.empty(),
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                            "Your site \"${monitor.name}\" (${monitor.url}) has an INVALID certificate"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldNotContain OffsetDateTime.MAX.toString()
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.empty(),
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.just(firstSSLRecord),
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "ssl error1"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.empty(),
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has an INVALID certificate"
                    notificationsSent[1] shouldContain "has a VALID certificate"
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.just(firstSSLRecord),
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has a VALID certificate"
                    notificationsSent[1] shouldContain "has an INVALID certificate"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                            "Your SSL certificate for ${monitor.url} will expire soon"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldNotContain OffsetDateTime.MAX.toString()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has a VALID certificate"
                    notificationsSent[1] shouldContain "Your SSL certificate for ${monitor.url} will expire soon"
                }
            }
        }

        given("the TelegramEventHandler - error handling logic") {
            `when`("it receives an event but an error happens when it calls the webhook") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                mockHttpErrorResponse()

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (200)"
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockSuccessfulHttpResponse() {
        every {
            mockHttpClient.exchange<SlackWebhookMessage, String, String>(any(), Argument.STRING, Argument.STRING)
        } returns Flowable.just(
            HttpResponse.ok()
        )
    }

    private fun mockHttpErrorResponse() {
        every {
            mockHttpClient.exchange<SlackWebhookMessage, String, String>(any(), Argument.STRING, Argument.STRING)
        } returns Flowable.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }
}
