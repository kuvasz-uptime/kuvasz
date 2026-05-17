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
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
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
import com.kuvaszuptime.kuvasz.services.integrations.DiscordWebhookClient
import com.kuvaszuptime.kuvasz.services.integrations.DiscordWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
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
class DiscordEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    integrationRepository: IntegrationRepository,
    discordNotificationConfigs: List<DiscordNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {

    private val mockClient = mockk<DiscordWebhookClient>()

    private val globalDiscordConfig = discordNotificationConfigs.first { it.enabled && it.global }
    private val otherDiscordConfig = discordNotificationConfigs.first { it.enabled && !it.global }
    private val disabledDiscordConfig = discordNotificationConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()
        val discordWebhookService = DiscordWebhookService(mockClient)
        val webhookServiceSpy = spyk(discordWebhookService, recordPrivateCalls = true)

        DiscordEventHandler(webhookServiceSpy, eventDispatcher, integrationRepository)

        given("the DiscordEventHandler - HTTP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalDiscordConfig.id,
                        otherDiscordConfig.id,
                        disabledDiscordConfig.id,
                    ),
                    sensitiveUrl = true,
                )
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherDiscordConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledDiscordConfig, any()) }

                    slot.forAll { message ->
                        message shouldContain "Your monitor \"${monitor.name}\" (MASKED URL) is DOWN"
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

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationsSent)
                        )
                    }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured shouldContain "is DOWN (500)"
                }
            }
        }

        given("the DiscordEventHandler - PUSH UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    integrations = listOf(
                        globalDiscordConfig.id,
                        otherDiscordConfig.id,
                        disabledDiscordConfig.id,
                    )
                )
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "down error",
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherDiscordConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledDiscordConfig, any()) }

                    slot.forAll { message ->
                        message shouldBe "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
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

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    slot.captured shouldBe "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "error"
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<String>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0] shouldBe
                        "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
                    notificationsSent[1] shouldStartWith
                        "✅ **Your monitor \"${monitor.name}\" is UP**\nWas down for "
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    error = "missed heartbeat"
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured shouldStartWith
                        "🚨 **Your monitor \"${monitor.name}\" is DOWN**\nWas up for "
                }
            }
        }

        given("the DiscordEventHandler - ICMP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val event = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    integrations = listOf(
                        globalDiscordConfig.id,
                        otherDiscordConfig.id,
                        disabledDiscordConfig.id,
                    )
                )
                val event = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherDiscordConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledDiscordConfig, any()) }

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

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationsSent)
                        )
                    }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }
        }

        given("the DiscordEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalDiscordConfig.id,
                        otherDiscordConfig.id,
                        disabledDiscordConfig.id,
                    )
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherDiscordConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledDiscordConfig, any()) }
                    slot.forAll { message ->
                        message shouldContain
                            "Your site \"${monitor.name}\" (${monitor.url}) has an INVALID certificate"
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
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationsSent)
                        )
                    }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
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
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    slot.captured shouldContain originalValidTo.toString()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository, sensitiveUrl = true)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured shouldContain
                        "Your SSL certificate for \"${monitor.name}\" will expire soon"
                }
            }
        }

        given("the DiscordEventHandler - error handling logic") {
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

    private fun mockSuccessfulHttpResponse() {
        every {
            mockClient.sendMessage(any(), any())
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
