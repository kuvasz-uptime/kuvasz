package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
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
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.recipientAddresses
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
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
import com.kuvaszuptime.kuvasz.services.integrations.SMTPMailer
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
    private val tcpUptimeEventRepository: TcpUptimeEventRepository,
    private val dnsUptimeEventRepository: DnsUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    smtpMailer: SMTPMailer,
    integrationRepository: IntegrationRepository,
    emailNotificationConfigs: List<EmailNotificationConfig>,
    databaseEventHandler: DatabaseEventHandler,
) : EventHandlerTest(databaseEventHandler) {
    init {
        val eventDispatcher = EventDispatcher()
        val mailerSpy = spyk(smtpMailer, recordPrivateCalls = true)
        // It has multiple recipients and relies on the default separate emails, so it sends one email per recipient
        val globalEmailConfig = emailNotificationConfigs.first { it.global }
        // It has multiple recipients with separate emails disabled, so it sends one email with all of them in To
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
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.GATEWAY_TIMEOUT,
                    error = Exception("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldContain "500 Internal Server Error"
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe expectedEmail.subject
                    }
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

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldContain "Latency: 1000ms"
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is UP"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send an email only about the down event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "Second error",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldContain "First error"
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe expectedEmail.subject
                    }
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

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is UP"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send an email only about the down event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }
        }

        given("the SMTPEventHandler - ICMP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val event = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createIcmpMonitor(
                    repository = icmpMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = null,
                    packetLossPercentage = 100
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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

                then("it should not send out any email about them") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe expectedEmail.subject
                    }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                    packetLossPercentage = 0
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is UP"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = icmpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = IcmpMonitorDownEvent(
                    monitor = monitor,
                    error = "Packet loss: 100% (sent=3, received=0)",
                    previousEvent = firstUptimeRecord,
                    packetLossPercentage = 100
                )
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send an email only about the down event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }
        }

        given("the SMTPEventHandler - TCP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val event = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createTcpMonitor(
                    repository = tcpMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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

                then("it should not send out any email about them") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()

                val secondEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe expectedEmail.subject
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val firstEvent = TcpMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = tcpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = TcpMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is UP"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send an email only about the down event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }
        }

        given("the SMTPEventHandler - DNS UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val event = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                    latencyInMs = 5,
                )

                eventDispatcher.testDispatch(event)

                then("it should not send an email about the event") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createDnsMonitor(
                    repository = dnsMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "is DOWN"
                    }
                    sentEmails.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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

                then("it should not send out any email about them") {
                    verify(inverse = true) { mailerSpy.sendAsync(any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                val expectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()

                val secondEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = firstUptimeRecord,
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe expectedEmail.subject
                    }
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val firstEvent = DnsMonitorDownEvent(
                    monitor = monitor,
                    error = "Connection refused",
                    previousEvent = null,
                )
                eventDispatcher.testDispatch(firstEvent)
                val firstUptimeRecord = dnsUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = DnsMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord,
                    latencyInMs = 5,
                )
                eventDispatcher.testDispatch(secondEvent)

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is UP"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromUptimeEvent(secondEvent).first()

                then("it should send an email only about the down event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "is DOWN"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }
        }

        given("the SMTPEventHandler - DNS drift events") {
            `when`("it receives a DnsRecordsChangedEvent") {
                val monitor = createDnsMonitor(
                    repository = dnsMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = DnsRecordsChangedEvent(
                    monitor = monitor,
                    previousRecords = mapOf(DnsRecordType.A to listOf("1.1.1.1")),
                    currentRecords = mapOf(DnsRecordType.A to listOf("2.2.2.2")),
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromDnsRecordsChangedEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send a drift email to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "[${monitor.name}] DNS records have changed"
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
                    }
                }
            }
        }

        given("the SMTPEventHandler - Docker restart events") {
            `when`("it receives a DockerContainerRestartedEvent") {
                val monitor = createDockerMonitor(
                    repository = dockerMonitorRepository,
                    integrations = listOf(
                        globalEmailConfig.id,
                        otherEmailConfig.id,
                        disabledEmailConfig.id,
                    ),
                )
                val event = DockerContainerRestartedEvent(
                    monitor = monitor,
                    previousRestartCount = 2,
                    currentRestartCount = 3,
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromDockerContainerRestartedEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send a restart email to every enabled integration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "[${monitor.name}] The container has been restarted"
                    }
                    sentEmails.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
                    }
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
                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event to every enabled integrations") {
                    val slot = mutableListOf<Email>()

                    verify(exactly = 3) { mailerSpy.sendAsync(capture(slot)) }
                    slot.forAll { email ->
                        // Email text and subject are not config specific
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldBe expectedEmail.subject
                        email.subject shouldContain "has an INVALID"
                    }
                    slot.filter { email -> email.fromRecipient?.address == globalEmailConfig.fromAddress }
                        .shouldBeSentSeparatelyTo(globalEmailConfig)
                    slot.forOne { fromOtherConfig ->
                        fromOtherConfig.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        fromOtherConfig.toRecipients.map { it.address } shouldContainExactly
                            otherEmailConfig.recipientAddresses()
                    }
                    slot.forNone { fromDisabledConfig ->
                        fromDisabledConfig.fromRecipient.shouldNotBeNull().address shouldBe
                            disabledEmailConfig.fromAddress
                        fromDisabledConfig.toRecipients.map { it.address } shouldContainExactly
                            disabledEmailConfig.recipientAddresses()
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

                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent).first()
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldContain "ssl error1"
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "has an INVALID"
                        email.subject shouldBe expectedEmail.subject
                    }
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

                val firstExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent).first()
                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent).first()

                then("it should send two different emails about them to each recipient") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 4) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent.take(2).forAll { email ->
                        email.plainText shouldBe firstExpectedEmail.plainText
                        email.subject shouldContain "has an INVALID"
                        email.subject shouldBe firstExpectedEmail.subject
                    }
                    emailsSent.drop(2).forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "has a VALID"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
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

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent).first()

                then("it should send an email, only about the invalidity") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "has an INVALID"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(event).first()

                eventDispatcher.testDispatch(event)

                then("it should send an email about the event") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "will expire soon"
                        email.subject shouldBe expectedEmail.subject
                    }
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

                val expectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(firstEvent).first()
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(10)),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                then("it should send only one email about them to each recipient") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "will expire soon"
                        email.subject shouldBe expectedEmail.subject
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
                eventDispatcher.testDispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.testDispatch(secondEvent)

                val secondExpectedEmail = EmailFactory(globalEmailConfig).fromSSLEvent(secondEvent).first()

                then("it should send an email, only about the expiration") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.shouldBeSentSeparatelyTo(globalEmailConfig)
                    sentEmails.forAll { email ->
                        email.plainText shouldBe secondExpectedEmail.plainText
                        email.subject shouldContain "will expire soon"
                        email.subject shouldBe secondExpectedEmail.subject
                    }
                }
            }
        }

        given("the SMTPEventHandler - maintenance window events") {
            `when`("it receives a MaintenanceWindowStartEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    description = "Planned upgrade",
                    integrations = listOf(otherEmailConfig.id, disabledEmailConfig.id),
                )
                val startEvent = MaintenanceWindowStartEvent(window)
                val expectedEmail = EmailFactory(otherEmailConfig).fromMaintenanceEvent(startEvent).single()

                eventDispatcher.dispatch(startEvent)

                then("it emails only assigned & enabled integrations, ignoring global ones") {
                    val sentEmails = mutableListOf<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(sentEmails)) }
                    sentEmails.forAll { email ->
                        email.plainText shouldBe expectedEmail.plainText
                        email.subject shouldContain "has started"
                    }
                    sentEmails.forOne { email ->
                        email.fromRecipient.shouldNotBeNull().address shouldBe otherEmailConfig.fromAddress
                        email.toRecipients.map { it.address } shouldContainExactly otherEmailConfig.recipientAddresses()
                    }
                    sentEmails.forNone { email ->
                        email.fromRecipient.shouldNotBeNull().address shouldBe globalEmailConfig.fromAddress
                    }
                    sentEmails.forNone { email ->
                        email.fromRecipient.shouldNotBeNull().address shouldBe disabledEmailConfig.fromAddress
                    }
                }
            }

            `when`("it receives a MaintenanceWindowEndEvent with explicitly assigned integrations") {
                val window = createMaintenanceWindow(
                    dslContext,
                    integrations = listOf(otherEmailConfig.id),
                )
                val endEvent = MaintenanceWindowEndEvent(window)

                eventDispatcher.dispatch(endEvent)

                then("it emails the assigned integration about the window ending") {
                    val emailSent = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(emailSent)) }
                    emailSent.captured.subject shouldContain "has ended"
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}

private fun List<Email>.shouldBeSentSeparatelyTo(config: EmailNotificationConfig) {
    map { email -> email.toRecipients.single().address } shouldContainExactlyInAnyOrder config.recipientAddresses()
}
