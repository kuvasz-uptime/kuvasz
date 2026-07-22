package com.kuvaszuptime.kuvasz.handlers

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
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyEventAction
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
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
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.PagerdutyAPIClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.rxjava3.core.Single

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class PagerdutyEventHandlerTest(
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
    sslEventRepository: SSLEventRepository,
    integrationRepository: IntegrationRepository,
    pagerdutyConfigs: List<PagerdutyConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {
    private val mockClient = mockk<PagerdutyAPIClient>()

    private val globalPagerdutyConfig = pagerdutyConfigs.first { it.global }
    private val otherPagerdutyConfig = pagerdutyConfigs.first { !it.global && it.enabled }
    private val disabledPagerdutyConfig = pagerdutyConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()

        PagerdutyEventHandler(eventDispatcher, mockClient, integrationRepository)

        given("the PagerdutyEventHandler - HTTP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
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
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    }

                    triggerSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }

                    resolveSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.RESOLVE
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    }
                    resolveSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Exception()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - PUSH UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "irrelevant",
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "irrelevant"
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    }

                    triggerSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }

                    resolveSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.RESOLVE
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    }
                    resolveSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    error = "irrelevant",
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - ICMP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val event = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )

                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100,
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
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
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                    packetLossPercentage = 0,
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    }
                    triggerSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }

                    resolveSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.RESOLVE
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    }
                    resolveSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - TCP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val event = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(
                    tcpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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
                    latencyInMs = 8,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createTcpMonitor(
                    tcpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    }
                    triggerSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }

                    resolveSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.RESOLVE
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    }
                    resolveSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - DNS UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val event = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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
                    latencyInMs = 8,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    }
                    triggerSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    triggerSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }

                    resolveSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.RESOLVE
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    }
                    resolveSlot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    resolveSlot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - DNS drift events") {
            `when`("it receives a DnsRecordsChangedEvent") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger a warning alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldStartWith "kuvasz_dns_drift_${monitor.id}_"
                        request.payload.severity shouldBe PagerdutySeverity.WARNING
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
                }
            }

            `when`("it receives two DnsRecordsChangedEvents with different answer sets") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(globalPagerdutyConfig.id),
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(
                    DnsRecordsChangedEvent(
                        monitor = monitor,
                        previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                        currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                    )
                )
                eventDispatcher.testDispatch(
                    DnsRecordsChangedEvent(
                        monitor = monitor,
                        previousRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                        currentRecords = mapOf(DnsRecordType.A to listOf("3.3.3.3")),
                    )
                )

                then("each change gets its own dedup key, so PD does not fold the second one into the first") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.map { it.dedupKey }.toSet() shouldHaveSize 2
                }
            }

            `when`("it receives a DnsRecordsChangedEvent arriving at the same answer set twice") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    integrations = listOf(globalPagerdutyConfig.id),
                )
                mockSuccessfulTriggerResponse()

                // A monitor flapping between two answer sets keeps landing back on the one it already alerted about
                eventDispatcher.testDispatch(
                    DnsRecordsChangedEvent(
                        monitor = monitor,
                        previousRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                        currentRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    )
                )
                eventDispatcher.testDispatch(
                    DnsRecordsChangedEvent(
                        monitor = monitor,
                        previousRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                        currentRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    )
                )

                then("both carry the same dedup key, so the flapping folds onto a single incident") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.map { it.dedupKey }.toSet() shouldHaveSize 1
                }
            }
        }

        given("the PagerdutyEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.testDispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_ssl_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.name
                        request.payload.summary shouldBe event.toStructuredMessage().summary
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.routingKey shouldBe globalPagerdutyConfig.integrationKey
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.routingKey shouldBe otherPagerdutyConfig.integrationKey
                    }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
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
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should trigger an alert and then resolve it") {
                    val triggerSlot = slot<PagerdutyTriggerRequest>()
                    val resolveSlot = slot<PagerdutyResolveRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 1) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    triggerSlot.captured.payload.severity shouldBe PagerdutySeverity.CRITICAL
                    triggerSlot.captured.payload.source shouldBe monitor.name
                    triggerSlot.captured.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    triggerSlot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey

                    resolveSlot.captured.eventAction shouldBe PagerdutyEventAction.RESOLVE
                    resolveSlot.captured.dedupKey shouldBe triggerSlot.captured.dedupKey
                    resolveSlot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.testDispatch(event)

                then("it should trigger an alert with WARNING severity") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.dedupKey shouldBe "kuvasz_ssl_${monitor.id}"
                    slot.captured.payload.summary shouldBe event.toStructuredMessage().summary
                    slot.captured.payload.source shouldBe event.monitor.name
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous SSLValidEvent") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.testDispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - maintenance window events") {
            `when`("it receives a MaintenanceWindowStartEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    description = "Planned upgrade",
                    integrations = listOf(otherPagerdutyConfig.id, disabledPagerdutyConfig.id),
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.dispatch(MaintenanceWindowStartEvent(window))

                then("it triggers a WARNING alert only for assigned & enabled integrations, ignoring global ones") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_maintenance_${window.id}"
                        request.payload.severity shouldBe PagerdutySeverity.WARNING
                        request.payload.source shouldBe window.name
                    }
                    slot.forOne { it.routingKey shouldBe otherPagerdutyConfig.integrationKey }
                    slot.forNone { it.routingKey shouldBe globalPagerdutyConfig.integrationKey }
                    slot.forNone { it.routingKey shouldBe disabledPagerdutyConfig.integrationKey }
                }
            }

            `when`("it receives a MaintenanceWindowEndEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherPagerdutyConfig.id),
                )
                mockSuccessfulResolveResponse()

                eventDispatcher.dispatch(MaintenanceWindowEndEvent(window))

                then("it resolves the alert with the matching deduplication key") {
                    val slot = slot<PagerdutyResolveRequest>()

                    verify(exactly = 1) { mockClient.resolveAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.RESOLVE
                    slot.captured.dedupKey shouldBe "kuvasz_maintenance_${window.id}"
                    slot.captured.routingKey shouldBe otherPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - error handling logic") {
            `when`("an error happens when it calls the API") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockErrorTriggerResponse()

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.testDispatch(event) }
                    verify(exactly = 1) { mockClient.triggerAlert(any()) }
                }
            }

            `when`("a non-HTTP error happens when it calls the API") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                every { mockClient.triggerAlert(any()) } returns Single.error(RuntimeException("boom"))

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.testDispatch(event) }
                    verify(exactly = 1) { mockClient.triggerAlert(any()) }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }

    private fun mockSuccessfulTriggerResponse() {
        every {
            mockClient.triggerAlert(any())
        } returns Single.just("irrelevant")
    }

    private fun mockErrorTriggerResponse() {
        every {
            mockClient.triggerAlert(any())
        } returns Single.error(
            HttpClientResponseException("error", HttpResponse.badRequest("bad_request"))
        )
    }

    private fun mockSuccessfulResolveResponse() {
        every {
            mockClient.resolveAlert(any())
        } returns Single.just("irrelevant")
    }
}
