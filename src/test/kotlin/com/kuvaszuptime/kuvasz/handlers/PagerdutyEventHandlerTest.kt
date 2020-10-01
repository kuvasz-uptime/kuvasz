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
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyEventAction
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyResolveRequest
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutySeverity
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyTriggerRequest
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.PagerdutyAPIClient
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.Single

@MicronautTest(startApplication = false)
class PagerdutyEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository
) : DatabaseBehaviorSpec() {
    private val mockClient = mockk<PagerdutyAPIClient>()

    init {
        val eventDispatcher = EventDispatcher()

        DatabaseEventHandler(eventDispatcher, uptimeEventRepository, latencyLogRepository, sslEventRepository)
        PagerdutyEventHandler(eventDispatcher, mockClient)

        given("the PagerdutyEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
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
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.dispatch(event)

                then("it should trigger an alert on PD") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.dedupKey shouldBe "kuvasz_uptime_${monitor.id}"
                    slot.captured.payload.severity shouldBe PagerdutySeverity.CRITICAL
                    slot.captured.payload.source shouldBe monitor.url
                    slot.captured.payload.summary shouldBe event.toStructuredMessage().summary
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

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
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("First error"),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.NOT_FOUND,
                    error = Throwable("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                mockSuccessfulResolveResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should trigger an alert and then resolve it") {
                    val triggerSlot = slot<PagerdutyTriggerRequest>()
                    val resolveSlot = slot<PagerdutyResolveRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(triggerSlot)) }
                    verify(exactly = 1) { mockClient.resolveAlert(capture(resolveSlot)) }
                    triggerSlot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    triggerSlot.captured.dedupKey shouldBe resolveSlot.captured.dedupKey
                    resolveSlot.captured.eventAction shouldBe PagerdutyEventAction.RESOLVE
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = firstUptimeRecord,
                    error = Throwable()
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(secondEvent)

                then("it should call only triggerAlert()") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                }
            }

            `when`("it should call PD but monitor has no routing key set") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = null)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                eventDispatcher.dispatch(event)

                then("it should not call PD's API") {
                    verify(exactly = 0) { mockClient.triggerAlert(any()) }
                }
            }
        }

        given("the PagerdutyEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
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
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                mockSuccessfulTriggerResponse()

                eventDispatcher.dispatch(event)

                then("it should trigger an alert on PD") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                    slot.captured.eventAction shouldBe PagerdutyEventAction.TRIGGER
                    slot.captured.dedupKey shouldBe "kuvasz_ssl_${monitor.id}"
                    slot.captured.payload.severity shouldBe PagerdutySeverity.CRITICAL
                    slot.captured.payload.source shouldBe monitor.url
                    slot.captured.payload.summary shouldBe event.toStructuredMessage().summary
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

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
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

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
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

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
                    triggerSlot.captured.dedupKey shouldBe resolveSlot.captured.dedupKey
                    resolveSlot.captured.eventAction shouldBe PagerdutyEventAction.RESOLVE
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

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
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
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
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulTriggerResponse()
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should call triggerAlert() only once") {
                    val slot = slot<PagerdutyTriggerRequest>()

                    verify(exactly = 1) { mockClient.triggerAlert(capture(slot)) }
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous SSLValidEvent") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

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
                }
            }
        }

        given("the PagerdutyEventHandler - error handling logic") {
            `when`("an error happens when it calls the API") {
                val monitor = createMonitor(monitorRepository, pagerdutyIntegrationKey = "something")
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                mockErrorTriggerResponse()

                then("it should not throw an exception") {
                    shouldNotThrowAny { eventDispatcher.dispatch(event) }
                    verify(exactly = 1) { mockClient.triggerAlert(any()) }
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
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
