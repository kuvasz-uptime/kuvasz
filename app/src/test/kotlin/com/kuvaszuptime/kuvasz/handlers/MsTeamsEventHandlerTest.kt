package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.MsTeamsCardFactory
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.handlers.CardContainer
import com.kuvaszuptime.kuvasz.models.handlers.CardTextBlock
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsMessage
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.MsTeamsWebhookClient
import com.kuvaszuptime.kuvasz.services.integrations.MsTeamsWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
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
class MsTeamsEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val tcpUptimeEventRepository: TcpUptimeEventRepository,
    private val dnsUptimeEventRepository: DnsUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    integrationRepository: IntegrationRepository,
    msTeamsNotificationConfigs: List<MsTeamsNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {

    private val mockClient = mockk<MsTeamsWebhookClient>()

    private val globalMsTeamsConfig = msTeamsNotificationConfigs.first { it.enabled && it.global }
    private val otherMsTeamsConfig = msTeamsNotificationConfigs.first { it.enabled && !it.global }
    private val disabledMsTeamsConfig = msTeamsNotificationConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()
        val msTeamsWebhookService = MsTeamsWebhookService(mockClient, MsTeamsCardFactory())
        val webhookServiceSpy = spyk(msTeamsWebhookService, recordPrivateCalls = true)

        MsTeamsEventHandler(eventDispatcher, webhookServiceSpy, integrationRepository)

        given("the MsTeamsEventHandler - HTTP UPTIME events") {
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
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
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
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldContain "Your monitor \"${monitor.name}\" (MASKED URL) is DOWN"
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "(500)"
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
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldContain "is DOWN (500)"
                    notificationsSent[1].allText() shouldContain "Latency: 1000ms"
                    notificationsSent[1].allText() shouldContain "is UP (200)"
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
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain "is DOWN (500)"
                }
            }
        }

        given("the MsTeamsEventHandler - PUSH UPTIME events") {
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
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
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
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldBe "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldBe "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
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
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldBe
                        "🚨 **Your monitor \"${monitor.name}\" is DOWN**"
                    notificationsSent[1].allText() shouldStartWith
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
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldStartWith
                        "🚨 **Your monitor \"${monitor.name}\" is DOWN**\nWas up for "
                }
            }
        }

        given("the MsTeamsEventHandler - ICMP UPTIME events") {
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
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
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
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
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
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    notificationsSent[1].allText() shouldContain "Your monitor \"${monitor.name}\" is UP"
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
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }
        }

        given("the MsTeamsEventHandler - TCP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val event = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(
                    tcpMonitorRepository,
                    integrations = listOf(
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
                    )
                )
                val event = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 6,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    notificationsSent[1].allText() shouldContain "Your monitor \"${monitor.name}\" is UP"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }
        }

        given("the MsTeamsEventHandler - DNS UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val event = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
                    )
                )
                val event = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 6,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                    notificationsSent[1].allText() shouldContain "Your monitor \"${monitor.name}\" is UP"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                mockSuccessfulHttpResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain "Your monitor \"${monitor.name}\" is DOWN"
                }
            }
        }

        given("the MsTeamsEventHandler - DNS drift events") {
            `when`("it receives a DnsRecordsChangedEvent") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
                    )
                )
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.testDispatch(event)

                then("it should send a drift notification to all enabled integrations") {
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.forAll { message ->
                        message.allText() shouldContain "DNS records changed for monitor \"${monitor.name}\""
                    }
                }
            }
        }

        given("the MsTeamsEventHandler - SSL events") {
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
                        globalMsTeamsConfig.id,
                        otherMsTeamsConfig.id,
                        disabledMsTeamsConfig.id,
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
                    val slot = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }
                    slot.forAll { message ->
                        message.allText() shouldContain
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "ssl error1"
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
                    val notificationsSent = mutableListOf<MsTeamsMessage>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].allText() shouldContain "has an INVALID certificate"
                    notificationsSent[1].allText() shouldContain "has a VALID certificate"
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
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain "has an INVALID certificate"
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain
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
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain originalValidTo.toString()
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
                    val notificationSent = slot<MsTeamsMessage>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalMsTeamsConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.allText() shouldContain
                        "Your SSL certificate for \"${monitor.name}\" will expire soon"
                }
            }
        }

        given("the MsTeamsEventHandler - maintenance window events") {
            `when`("it receives a MaintenanceWindowStartEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    description = "Planned upgrade",
                    integrations = listOf(otherMsTeamsConfig.id, disabledMsTeamsConfig.id),
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))

                then("it should notify only the assigned & enabled integrations, ignoring global ones") {
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(globalMsTeamsConfig, any()) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledMsTeamsConfig, any()) }

                    slot.captured.allText() shouldContain "Maintenance \"${window.name}\" has started"
                    slot.captured.allText() shouldContain "Planned upgrade"
                }
            }

            `when`("it receives a MaintenanceWindowEndEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherMsTeamsConfig.id),
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))

                then("it should notify the assigned integration with an end message") {
                    val slot = slot<MsTeamsMessage>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherMsTeamsConfig, capture(slot)) }
                    slot.captured.allText() shouldContain "Maintenance \"${window.name}\" has ended"
                }
            }
        }

        given("the MsTeamsEventHandler - error handling logic") {
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

    /**
     * The rendered card, flattened back into the very same text the other chat integrations would send,
     * so the expectations can stay comparable with theirs.
     **/
    private fun MsTeamsMessage.allText(): String =
        attachments.single().content.body
            .flatMap { element ->
                when (element) {
                    is CardContainer -> element.items
                    is CardTextBlock -> listOf(element)
                }
            }
            .filterIsInstance<CardTextBlock>()
            .joinToString("\n") { it.text }

    private fun mockHttpErrorResponse() {
        every {
            mockClient.sendMessage(any(), any())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }
}
