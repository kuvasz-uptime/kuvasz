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
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyEventAction
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.PagerdutyAPIClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.shouldBe
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
import org.jooq.DSLContext

@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class PagerdutyEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository,
    dslContext: DSLContext,
    integrationRepository: IntegrationRepository,
    pagerdutyConfigs: List<PagerdutyConfig>,
) : DatabaseBehaviorSpec() {
    private val mockClient = mockk<PagerdutyAPIClient>()

    private val globalPagerdutyConfig = pagerdutyConfigs.first { it.global }
    private val otherPagerdutyConfig = pagerdutyConfigs.first { !it.global && it.enabled }
    private val disabledPagerdutyConfig = pagerdutyConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()

        DatabaseEventHandler(
            eventDispatcher,
            uptimeEventRepository,
            latencyLogRepository,
            sslEventRepository,
            dslContext,
        )
        PagerdutyEventHandler(eventDispatcher, mockClient, integrationRepository)

        given("the PagerdutyEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.dispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    monitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.dispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.url
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

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
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
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(
                    monitorRepository,
                    integrations = listOf(
                        globalPagerdutyConfig.id,
                        otherPagerdutyConfig.id,
                        disabledPagerdutyConfig.id,
                    )
                )
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should trigger an alert and then resolve it for each enabled integration") {
                    val triggerSlot = mutableListOf<PagerdutyTriggerRequest>()
                    val resolveSlot = mutableListOf<PagerdutyResolveRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 2) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.url
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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Exception()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    monitorRepository,
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

                eventDispatcher.dispatch(event)

                then("it should trigger an alert on PD for each enabled integration") {
                    val slot = mutableListOf<PagerdutyTriggerRequest>()

                    verify(exactly = 2) { mockClient.triggerAlert(capture(slot)) }
                    slot.forAll { request ->
                        request.eventAction shouldBe PagerdutyEventAction.TRIGGER
                        request.dedupKey shouldBe "kuvasz_ssl_${monitor.id}"
                        request.payload.severity shouldBe PagerdutySeverity.CRITICAL
                        request.payload.source shouldBe monitor.url
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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not call the PD API") {
                    verify(exactly = 0) { mockClient.resolveAlert(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should trigger an alert and then resolve it") {
                    val triggerSlot = slot<PagerdutyTriggerRequest>()
                    val resolveSlot = slot<PagerdutyResolveRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 1) { mockClient.resolveAlert(capture(resolveSlot)) }

                    triggerSlot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    triggerSlot.captured.payload.severity shouldBe PagerdutySeverity.CRITICAL
                    triggerSlot.captured.payload.source shouldBe monitor.url
                    triggerSlot.captured.payload.summary shouldBe firstEvent.toStructuredMessage().summary
                    triggerSlot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey

                    resolveSlot.captured.eventAction shouldBe PagerdutyEventAction.RESOLVE
                    resolveSlot.captured.dedupKey shouldBe triggerSlot.captured.dedupKey
                    resolveSlot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.dispatch(event)

                then("it should trigger an alert with WARNING severity") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.dedupKey shouldBe "kuvasz_ssl_${monitor.id}"
                    slot.captured.payload.summary shouldBe event.toStructuredMessage().summary
                    slot.captured.payload.source shouldBe event.monitor.url
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous SSLValidEvent") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.payload.severity shouldBe PagerdutySeverity.WARNING
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.routingKey shouldBe globalPagerdutyConfig.integrationKey
                }
            }
        }

        given("the PagerdutyEventHandler - error handling logic") {
            `when`("an error happens when it calls the API") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                mockErrorTriggerResponse()

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
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
