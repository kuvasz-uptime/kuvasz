package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SMTPMailer
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.simplejavamail.api.email.Email

@SMTPTest
@MicronautTest(startApplication = false, environments = ["full-integrations-setup"])
class SMTPEventHandlerTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    smtpMailer: SMTPMailer,
    integrationRepository: IntegrationRepository,
    emailNotificationConfigs: List<EmailNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {
    init {
        val eventDispatcher = EventDispatcher()
        val mailerSpy = spyk(smtpMailer, recordPrivateCalls = true)
        val globalEmailConfig = emailNotificationConfigs.first { it.global }
        val otherEmailConfig = emailNotificationConfigs.first { !it.global && it.enabled }
        val disabledEmailConfig = emailNotificationConfigs.first { !it.enabled }

        SMTPEventHandler(mailerSpy, eventDispatcher, integrationRepository)

        given("the SMTPEventHandler - HTTP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    repository = httpMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = null
                )
                // Email text and subject are not config specific
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event)

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.forOne { fromGlobalConfig ->
                        fromGlobalConfig.fromRecipient.shouldNotBeNull().address shouldBe globalEmailConfig.fromAddress
                        fromGlobalConfig.toRecipients.single().address shouldBe globalEmailConfig.toAddress
                    }
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.single().address shouldBe otherEmailConfig.toAddress
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.single().address shouldBe disabledEmailConfig.toAddress
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

                then("it should not send out any email about them") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent)

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.GATEWAY_TIMEOUT,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldContain "500 Internal Server Error"
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "is DOWN"
                    slot.captured.subject shouldBe expectedEmail.subject
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent)
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "is DOWN"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldContain "Latency: 1000ms"
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "is UP"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
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
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent)

                then("it should send an email only about the down event") {
                    val emailSent = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(emailSent)) }
                    emailSent.captured.plainText shouldBe secondExpectedEmail.plainText
                    emailSent.captured.subject shouldContain "is DOWN"
                    emailSent.captured.subject shouldBe secondExpectedEmail.subject
                }
            }
        }

        given("the SMTPEventHandler - PUSH UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(
                    repository = pushMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Some error",
                    previousEvent = null
                )
                // Email text and subject are not config specific
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event)

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.forOne { fromGlobalConfig ->
                        fromGlobalConfig.fromRecipient.shouldNotBeNull().address shouldBe globalEmailConfig.fromAddress
                        fromGlobalConfig.toRecipients.single().address shouldBe globalEmailConfig.toAddress
                    }
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.single().address shouldBe otherEmailConfig.toAddress
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.single().address shouldBe disabledEmailConfig.toAddress
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

                then("it should not send out any email about them") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "First error",
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent)

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldContain "First error"
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "is DOWN"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "Some error",
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent)
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "is DOWN"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "is UP"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
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
                    error = "Some error",
                )
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent)

                then("it should send an email only about the down event") {
                    val emailSent = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(emailSent)) }
                    emailSent.captured.plainText shouldBe secondExpectedEmail.plainText
                    emailSent.captured.subject shouldContain "is DOWN"
                    emailSent.captured.subject shouldBe secondExpectedEmail.subject
                }
            }
        }

        given("the SMTPEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(
                    repository = httpMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    )
                )
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                // Email text and subject are not config specific
                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(event)

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integrations") {
                    val slot = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(slot)) }
                    slot.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "has an INVALID"
                    }
                    slot.forOne { fromGlobalConfig ->
                        fromGlobalConfig.fromRecipient.shouldNotBeNull().address shouldBe globalEmailConfig.fromAddress
                        fromGlobalConfig.toRecipients.single().address shouldBe globalEmailConfig.toAddress
                    }
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.single().address shouldBe otherEmailConfig.toAddress
                    }
                    slot.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.single().address shouldBe disabledEmailConfig.toAddress
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

                then("it should not send an email about the events") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                eventDispatcher.testDispatch(firstEvent)

                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldContain "ssl error1"
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "has an INVALID"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent)
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "has an INVALID"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "has a VALID"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
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
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent)

                then("it should send an email, only about the invalidity") {
                    val emailSent = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(emailSent)) }
                    emailSent.captured.plainText shouldBe secondExpectedEmail.plainText
                    emailSent.captured.subject shouldContain "has an INVALID"
                    emailSent.captured.subject shouldBe secondExpectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(event)

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "will expire soon"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.testDispatch(firstEvent)

                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "will expire soon"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
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
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent)

                then("it should send an email, only about the expiration") {
                    val emailSent = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(emailSent)) }
                    emailSent.captured.plainText shouldBe secondExpectedEmail.plainText
                    emailSent.captured.subject shouldContain "will expire soon"
                    emailSent.captured.subject shouldBe secondExpectedEmail.subject
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
