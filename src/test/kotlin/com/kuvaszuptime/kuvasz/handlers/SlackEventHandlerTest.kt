package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.*
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SlackWebhookClient
import com.kuvaszuptime.kuvasz.services.SlackWebhookService
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.*
import io.reactivex.Single
import org.jooq.Configuration
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class SlackEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository,
    jooqConfig: Configuration
) : DatabaseBehaviorSpec() {
    private val mockClient = mockk<SlackWebhookClient>()

    init {
        val eventDispatcher = EventDispatcher()
        val slackWebhookService = SlackWebhookService(mockClient)
        val webhookServiceSpy = spyk(slackWebhookService, recordPrivateCalls = true)

        DatabaseEventHandler(
            eventDispatcher,
            uptimeEventRepository,
            latencyLogRepository,
            sslEventRepository,
            jooqConfig
        )
        SlackEventHandler(webhookServiceSpy, eventDispatcher)

        given("the SlackEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (200)"
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "Latency: 1000ms"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("First error"),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Throwable("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "(500)"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
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
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Throwable()
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "Latency: 1000ms"
                    notificationsSent[0] shouldContain "is UP (200)"
                    notificationsSent[1] shouldContain "is DOWN (500)"
                }
            }
        }

        given("the SlackEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                        "Your site \"${monitor.name}\" (${monitor.url}) has a VALID certificate"
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                        "Your site \"${monitor.name}\" (${monitor.url}) has an INVALID certificate"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldNotContain OffsetDateTime.MAX.toString()
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain "ssl error1"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has an INVALID certificate"
                    notificationsSent[1] shouldContain "has a VALID certificate"
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has a VALID certificate"
                    notificationsSent[1] shouldContain "has an INVALID certificate"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldContain
                        "Your SSL certificate for ${monitor.url} will expire soon"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(capture(slot)) }
                    slot.captured shouldNotContain OffsetDateTime.MAX.toString()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { webhookServiceSpy.sendMessage(capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has a VALID certificate"
                    notificationsSent[1] shouldContain "Your SSL certificate for ${monitor.url} will expire soon"
                }
            }
        }

        given("the SlackEventHandler - error handling logic") {
            `when`("it receives an event but an error happens when it calls the webhook") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                mockHttpErrorResponse()

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockSuccessfulHttpResponse() {
        every {
            mockClient.sendMessage(any())
        } returns Single.just("ok")
    }

    private fun mockHttpErrorResponse() {
        every {
            mockClient.sendMessage(any())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }
}
