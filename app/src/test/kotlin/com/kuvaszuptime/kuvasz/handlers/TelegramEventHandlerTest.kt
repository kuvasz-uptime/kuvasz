package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.TelegramAPIClient
import com.kuvaszuptime.kuvasz.services.integrations.TelegramAPIService
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
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

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class TelegramEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    httpUptimeEventRepository: HttpUptimeEventRepository,
    pushUptimeEventRepository: PushUptimeEventRepository,
    icmpUptimeEventRepository: IcmpUptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    telegramNotificationConfigs: List<TelegramNotificationConfig>,
    integrationRepository: IntegrationRepository,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {
    private val mockClient = mockk<TelegramAPIClient>()

    init {
        val eventDispatcher = EventDispatcher()
        val telegramAPIService = TelegramAPIService(mockClient)
        val apiServiceSpy = spyk(telegramAPIService, recordPrivateCalls = true)

        TelegramEventHandler(apiServiceSpy, eventDispatcher, integrationRepository)

        val globalTelegramConfig = telegramNotificationConfigs.first { it.global }
        val otherTelegramConfig = telegramNotificationConfigs.first { !it.global && it.enabled }
        val disabledTelegramConfig = telegramNotificationConfigs.first { !it.enabled }

        given("the TelegramEventHandler - HTTP UPTIME events") {

            `when`("it receives a MonitorUpEvent and There is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    repository = httpMonitorRepository,
                    monitorName = "testMonitor",
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    )
                )
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.testDispatch(event)

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
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception("First error"),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain "(500)"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "is DOWN (500)"
                    notificationsSent[1] shouldContain "Latency: 1000ms"
                    notificationsSent[1] shouldContain "is UP (200)"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Exception()
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "is DOWN (500)"
                }
            }
        }

        given("the TelegramEventHandler - PUSH UPTIME events") {

            `when`("it receives a MonitorUpEvent and There is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(
                    repository = pushMonitorRepository,
                    monitorName = "testMonitor",
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    )
                )
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "irrelevant",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.testDispatch(event)

                then("it should send a message about the event to every enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(otherTelegramConfig, capture(slot)) }
                    verify(inverse = true) { apiServiceSpy.sendMessage(disabledTelegramConfig, any()) }

                    slot.forAll { message ->
                        message shouldBe "🚨 <b>Your monitor \"testMonitor\" is DOWN</b>"
                    }

                    verify {
                        mockClient.sendMessage(globalTelegramConfig.apiToken, any())
                        mockClient.sendMessage(otherTelegramConfig.apiToken, any())
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldBe "🚨 <b>Your monitor \"${monitor.name}\" is DOWN</b>"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "irrelevant"
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "is DOWN"
                    notificationsSent[1] shouldContain "is UP"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    error = "irrelevant"
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldStartWith
                        "🚨 <b>Your monitor \"${monitor.name}\" is DOWN</b>\nWas up for "
                }
            }
        }

        given("the TelegramEventHandler - ICMP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val event = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(
                    repository = icmpMonitorRepository,
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    )
                )
                val event = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.testDispatch(event)

                then("it should send a message about the event to every enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(otherTelegramConfig, capture(slot)) }
                    verify(inverse = true) { apiServiceSpy.sendMessage(disabledTelegramConfig, any()) }

                    slot.forAll { message ->
                        message shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 6,
                    packetLossPercentage = 0
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    notificationsSent[1] shouldContain "Your monitor \"${monitor.name}\" is UP"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }
        }

        given("the TelegramEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "testMonitor",
                    integrations = listOf(
                        globalTelegramConfig.id,
                        otherTelegramConfig.id,
                        disabledTelegramConfig.id,
                    ),
                    sensitiveUrl = true,
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                mockSuccessfulHttpResponse(otherTelegramConfig.apiToken)

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to every enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    verify(exactly = 1) { apiServiceSpy.sendMessage(otherTelegramConfig, capture(slot)) }
                    verify(inverse = true) { apiServiceSpy.sendMessage(disabledTelegramConfig, any()) }
                    slot.forAll { message ->
                        message shouldContain "Your site \"testMonitor\" (MASKED URL) has an INVALID certificate"
                    }
                    verify {
                        mockClient.sendMessage(globalTelegramConfig.apiToken, any())
                        mockClient.sendMessage(otherTelegramConfig.apiToken, any())
                    }
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send any notification about them") {
                    verify(inverse = true) { apiServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain "ssl error1"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationsSent)) }
                    notificationsSent[0] shouldContain "has an INVALID certificate"
                    notificationsSent[1] shouldContain "has a VALID certificate"
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the invalid event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain "has an INVALID certificate"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)

                eventDispatcher.testDispatch(event)

                then("it should send a notification about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain
                        "Your SSL certificate for \"${monitor.name}\" will expire soon"
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val originalValidTo = getCurrentTimestamp()
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = originalValidTo),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(slot)) }
                    slot.captured shouldContain originalValidTo.toString()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with a VALID status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse(globalTelegramConfig.apiToken)
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the expiration") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) { apiServiceSpy.sendMessage(globalTelegramConfig, capture(notificationSent)) }
                    notificationSent.captured shouldContain
                        "Your SSL certificate for \"${monitor.name}\" will expire soon"
                }
            }
        }

        given("the TelegramEventHandler - error handling logic") {
            `when`("it receives an event but an error happens when it calls the webhook") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception("error"),
                    previousEvent = null,
                )
                mockHttpErrorResponse()

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.testDispatch(event) }
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
