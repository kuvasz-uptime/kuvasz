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
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.DiscordWebhookClient
import com.kuvaszuptime.kuvasz.services.DiscordWebhookService
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
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
class DiscordEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository,
    dslContext: DSLContext,
    integrationRepository: IntegrationRepository,
    discordNotificationConfigs: List<DiscordNotificationConfig>,
) : DatabaseBehaviorSpec() {

    private val mockClient = mockk<DiscordWebhookClient>()

    private val globalDiscordConfig = discordNotificationConfigs.first { it.enabled && it.global }
    private val otherDiscordConfig = discordNotificationConfigs.first { it.enabled && !it.global }
    private val disabledDiscordConfig = discordNotificationConfigs.first { !it.enabled }

    init {
        val eventDispatcher = EventDispatcher()
        val discordWebhookService = DiscordWebhookService(mockClient)
        val webhookServiceSpy = spyk(discordWebhookService, recordPrivateCalls = true)

        DatabaseEventHandler(
            eventDispatcher,
            uptimeEventRepository,
            latencyLogRepository,
            sslEventRepository,
            dslContext,
        )
        DiscordEventHandler(webhookServiceSpy, eventDispatcher, integrationRepository)

        given("the SlackEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.dispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    monitorRepository,
                    integrations = listOf(
                        globalDiscordConfig.id,
                        otherDiscordConfig.id,
                        disabledDiscordConfig.id,
                    )
                )
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()

                eventDispatcher.dispatch(event)

                then("it should send a webhook message about the event to all enabled integrations") {
                    val slot = mutableListOf<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    verify(exactly = 1) { webhookServiceSpy.sendMessage(otherDiscordConfig, capture(slot)) }
                    verify(inverse = true) { webhookServiceSpy.sendMessage(disabledDiscordConfig, any()) }

                    slot.forAll { message ->
                        message shouldContain "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN"
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

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
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

        given("the SlackEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.dispatch(event)

                then("it should not send a webhook message about the event") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(
                    monitorRepository,
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

                eventDispatcher.dispatch(event)

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
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not send notifications about them") {
                    verify(inverse = true) { webhookServiceSpy.sendMessage(any(), any()) }
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
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification about them") {
                    val slot = slot<String>()

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
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
                mockSuccessfulHttpResponse()
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

                    verify(exactly = 1) { webhookServiceSpy.sendMessage(globalDiscordConfig, capture(slot)) }
                    slot.captured shouldContain originalValidTo.toString()
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
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one notification, about the expiration") {
                    val notificationSent = slot<String>()

                    verify(exactly = 1) {
                        webhookServiceSpy.sendMessage(
                            globalDiscordConfig,
                            capture(notificationSent)
                        )
                    }
                    notificationSent.captured shouldContain "Your SSL certificate for ${monitor.url} will expire soon"
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
