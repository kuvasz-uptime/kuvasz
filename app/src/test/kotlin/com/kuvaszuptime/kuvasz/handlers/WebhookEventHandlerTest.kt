package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.WebhookMessageFactory
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
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
import com.kuvaszuptime.kuvasz.services.integrations.GenericWebhookClient
import com.kuvaszuptime.kuvasz.services.integrations.GenericWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseFactory
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
class WebhookEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    integrationRepository: IntegrationRepository,
    webhookConfigs: List<WebhookNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
    private val messageFactory: WebhookMessageFactory,
) : EventHandlerTest(databaseEventHandler) {
    private val mockClient = mockk<GenericWebhookClient>()

    private val globalWebhookConfig = webhookConfigs.first { it.enabled && it.global }
    private val otherWebhookConfig =
        webhookConfigs.first { it.enabled && !it.global && !it.excludedEvents.isNullOrEmpty() }
    private val noTemplateWebhookConfig =
        webhookConfigs.first { it.enabled && !it.global && it.payloadTemplate.isNullOrBlank() }
    private val disabledWebhookConfig = webhookConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()
        val webhookService = GenericWebhookService(mockClient, messageFactory)
        val webhookServiceSpy = spyk(webhookService)

        WebhookEventHandler(eventDispatcher, webhookServiceSpy, integrationRepository)

        given("the WebhookEventHandler - HTTP UPTIME events") {
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
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    ),
                    sensitiveUrl = true,
                )
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, any()) }
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(disabledWebhookConfig, any()) }
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
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
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
                mockSuccessfulHttpResponses()
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

                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    ),
                )
                val firstEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockSuccessfulHttpResponses()
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
                    val eventsSent = mutableListOf<MonitorEvent<*>>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(eventsSent)
                        )
                    }
                    eventsSent[0].shouldBeInstanceOf<HttpMonitorDownEvent>()
                    eventsSent[1].shouldBeInstanceOf<HttpMonitorUpEvent>()
                    // HTTP_UP events are excluded on the other integration
                    val eventsSentToOtherWebhook = mutableListOf<MonitorEvent<*>>()
                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            otherWebhookConfig,
                            capture(eventsSentToOtherWebhook),
                        )
                    }
                    eventsSentToOtherWebhook[0].shouldBeInstanceOf<HttpMonitorDownEvent>()
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
                mockSuccessfulHttpResponses()
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
                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any())
                    }
                }
            }
        }

        given("the WebhookEventHandler - PUSH UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    )
                )
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "irrelevant",
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {

                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, any()) }
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(disabledWebhookConfig, any()) }
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
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {

                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "First error"
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<MonitorEvent<*>>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].shouldBeInstanceOf<PushMonitorDownEvent>()
                    notificationsSent[1].shouldBeInstanceOf<PushMonitorUpEvent>()
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    error = "irrelevant",
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<MonitorEvent<*>>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.shouldBeInstanceOf<PushMonitorDownEvent>()
                }
            }
        }

        given("the WebhookEventHandler - ICMP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val event = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    )
                )
                val event = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100,
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, any()) }
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(disabledWebhookConfig, any()) }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 8,
                    packetLossPercentage = 0,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null,
                    packetLossPercentage = 100,
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    )
                )
                val firstEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100,
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<MonitorEvent<*>>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].shouldBeInstanceOf<IcmpMonitorDownEvent>()
                    notificationsSent[1].shouldBeInstanceOf<IcmpMonitorUpEvent>()
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val firstEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the down event") {
                    val notificationSent = slot<MonitorEvent<*>>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.shouldBeInstanceOf<IcmpMonitorDownEvent>()
                }
            }
        }

        given("the WebhookEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalWebhookConfig.id,
                        otherWebhookConfig.id,
                        disabledWebhookConfig.id,
                    )
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {

                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, any()) }
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(disabledWebhookConfig, any()) }
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
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {

                    verify(exactly = 1) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send two different notifications about them") {
                    val notificationsSent = mutableListOf<MonitorEvent<*>>()

                    verify(exactly = 2) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationsSent)
                        )
                    }
                    notificationsSent[0].shouldBeInstanceOf<SSLInvalidEvent>()
                    notificationsSent[1].shouldBeInstanceOf<SSLValidEvent>()
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null,
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the invalid event") {
                    val notificationSent = slot<MonitorEvent<*>>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.shouldBeInstanceOf<SSLInvalidEvent>()
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, sensitiveUrl = true)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.testDispatch(event)

                then("it should send a webhook message about the event") {
                    val slot = slot<MonitorEvent<*>>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(slot),
                        )
                    }
                    slot.captured.shouldBeInstanceOf<SSLWillExpireEvent>()
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
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification about them") {
                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            any(),
                        )
                    }
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponses()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one notification, about the expiration") {
                    val notificationSent = slot<MonitorEvent<*>>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(
                            globalWebhookConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured.shouldBeInstanceOf<SSLWillExpireEvent>()
                }
            }
        }

        given("the WebhookEventHandler - maintenance window events") {
            `when`("it receives a MaintenanceWindowStartEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    description = "Planned upgrade",
                    integrations = listOf(
                        otherWebhookConfig.id,
                        noTemplateWebhookConfig.id,
                        disabledWebhookConfig.id,
                    ),
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))

                then("it sends to assigned & enabled integrations only, with blanked monitor fields") {
                    val captured = slot<MaintenanceWindowEvent>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, capture(captured))
                    }
                    // A config without a payload template exercises the default GenericWebhookMessage payload path
                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(noTemplateWebhookConfig, any())
                    }
                    verify(inverse = true) { webhookServiceSpy.sendWebhookEvent(globalWebhookConfig, any()) }
                    verify(inverse = true) {
                        webhookServiceSpy.sendWebhookEvent(disabledWebhookConfig, any())
                    }

                    val message = messageFactory.fromEvent(captured.captured)
                    message.monitorId shouldBe 0
                    message.monitorUrn shouldBe ""
                    message.monitorName shouldBe ""
                    message.monitorDetailsUrl shouldBe ""
                    message.type shouldBe IntegrationEventType.MAINTENANCE_START
                    message.eventDetails shouldContain "Maintenance \"${window.name}\" has started"
                }
            }

            `when`("the assigned integration has a pre-existing monitor payloadTemplate") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherWebhookConfig.id),
                )
                mockSuccessfulHttpResponses()

                eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))

                then("the template still renders (the blank monitor vars resolve to empty) without error") {
                    val captured = slot<MaintenanceWindowEvent>()
                    verify(exactly = 1) {
                        webhookServiceSpy.sendWebhookEvent(otherWebhookConfig, capture(captured))
                    }
                    val rendered = messageFactory.fromEvent(
                        captured.captured,
                        otherWebhookConfig.payloadTemplate.shouldNotBeNull(),
                    )
                    rendered shouldContain "MAINTENANCE_END"
                }
            }
        }

        given("the WebhookEventHandler - error handling logic") {
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

    private fun mockSuccessfulHttpResponses() {
        every {
            mockClient.sendMessage(any(), any(), any(), any<String>())
        } returns Single.just(HttpResponseFactory.INSTANCE.ok())
        every {
            mockClient.sendMessage(any(), any(), any(), any<GenericWebhookMessage>())
        } returns Single.just(HttpResponseFactory.INSTANCE.ok())
    }

    private fun mockHttpErrorResponse() {
        every {
            mockClient.sendMessage(any(), any(), any(), any<String>())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
        every {
            mockClient.sendMessage(any(), any(), any(), any<GenericWebhookMessage>())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }
}
