package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.TelegramAPIClient
import com.kuvaszuptime.kuvasz.services.TelegramAPIService
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.jooq.DSLContext

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class TelegramEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    uptimeEventRepository: UptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository,
    dslContext: DSLContext,
    telegramNotificationConfigs: List<TelegramNotificationConfig>,
    integrationRepository: IntegrationRepository,
) : DatabaseBehaviorSpec() {
    private val mockClient = mockk<TelegramAPIClient>()

    init {
        val eventDispatcher = EventDispatcher()
        val telegramAPIService = TelegramAPIService(mockClient)
        val apiServiceSpy = spyk(telegramAPIService, recordPrivateCalls = true)

        DatabaseEventHandler(
            eventDispatcher,
            uptimeEventRepository,
            latencyLogRepository,
            sslEventRepository,
            dslContext,
        )
        TelegramEventHandler(apiServiceSpy, eventDispatcher, integrationRepository)

        val globalTelegramConfig = telegramNotificationConfigs.first { it.global }
        val otherTelegramConfig = telegramNotificationConfigs.first { !it.global && it.enabled }
        val disabledTelegramConfig = telegramNotificationConfigs.first { !it.enabled }

        given("the TelegramEventHandler") {
            `when`("it receives a MonitorUpEvent and There is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.dispatch(event)

                then("it should not send a message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    repository = monitorRepository,
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    )
                )
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.dispatch(event)

                then("it should send a message about the event to every enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(otherTelegramConfig, capture(slot)) }
                    verify(inverse = true) { apiServiceSpy.sendMessage(disabledTelegramConfig, any()) }

                    slot.forAll { message ->
                        message shouldContain "Your monitor \"testMonitor\" (http://irrelevant.com) is DOWN"
                    }

                    verify {
                        mockClient.sendMessage(globalTelegramConfig.apiToken, any())
                        mockClient.sendMessage(otherTelegramConfig.apiToken, any())
                    }
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
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception("First error"),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain "(500)"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
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
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Exception()
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "is DOWN (500)"
                }
            }
        }

        given("the TelegramEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.dispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    monitorRepository,
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    )
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event to every enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(otherTelegramConfig, capture(slot)) }
                    verify(inverse = true) { apiServiceSpy.sendMessage(disabledTelegramConfig, any()) }
                    slot.forAll { message ->
                        message shouldContain "Your site \"testMonitor\" (${monitor.url}) has an INVALID certificate"
                    }
                    verify {
                        mockClient.sendMessage(globalTelegramConfig.apiToken, any())
                        mockClient.sendMessage(otherTelegramConfig.apiToken, any())
                    }
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
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
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification, about the invalid event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "has an INVALID certificate"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)

                eventDispatcher.dispatch(event)

                then("it should send a notification about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain
                        "Your SSL certificate for ${monitor.url} will expire soon"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val originalValidTo = getCurrentTimestamp()
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = originalValidTo),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain originalValidTo.toString()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with a VALID status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification, about the expiration") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "Your SSL certificate for ${monitor.url} will expire soon"
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

    private fun mockSuccessfulHttpResponse(apiToken: String) {
        every {
            mockClient.sendMessage(apiToken, any())
        } returns Single.just("ok")
    }

    private fun mockHttpErrorResponse() {
        every {
            mockClient.sendMessage(any(), any())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }
}
