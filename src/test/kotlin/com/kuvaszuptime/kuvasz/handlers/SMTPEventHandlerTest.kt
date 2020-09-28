package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.handlers.SMTPEventHandlerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SMTPMailer
import com.kuvaszuptime.kuvasz.tables.SslEvent
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.simplejavamail.api.email.Email
import java.time.OffsetDateTime

@MicronautTest
class SMTPEventHandlerTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    latencyLogRepository: LatencyLogRepository,
    smtpEventHandlerConfig: SMTPEventHandlerConfig,
    smtpMailer: SMTPMailer
) : DatabaseBehaviorSpec() {
    init {
        val eventDispatcher = EventDispatcher()
        val emailFactory = EmailFactory(smtpEventHandlerConfig)
        val mailerSpy = spyk(smtpMailer, recordPrivateCalls = true)

        DatabaseEventHandler(eventDispatcher, uptimeEventRepository, latencyLogRepository, sslEventRepository)
        SMTPEventHandler(smtpEventHandlerConfig, mailerSpy, eventDispatcher)

        given("the SMTPEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                val expectedEmail = emailFactory.fromMonitorEvent(event)

                eventDispatcher.dispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "is UP"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = null
                )
                val expectedEmail = emailFactory.fromMonitorEvent(event)

                eventDispatcher.dispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "is DOWN"
                    slot.captured.subject shouldBe expectedEmail.subject
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
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)
                val expectedEmail = emailFactory.fromMonitorEvent(firstEvent)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldContain "Latency: 1000ms"
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "is UP"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("First error"),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)
                val expectedEmail = emailFactory.fromMonitorEvent(firstEvent)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("Second error"),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromMonitorEvent(secondEvent)

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
                val monitor = createMonitor(monitorRepository)
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
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromMonitorEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldContain "Latency: 1000ms"
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "is UP"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "is DOWN"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
                }
            }
        }

        given("the SMTPEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                val expectedEmail = emailFactory.fromMonitorEvent(event)

                eventDispatcher.dispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "has a VALID"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                val expectedEmail = emailFactory.fromMonitorEvent(event)

                eventDispatcher.dispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "has an INVALID"
                    slot.captured.subject shouldBe expectedEmail.subject
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
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)
                val expectedEmail = emailFactory.fromMonitorEvent(firstEvent)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.plainText shouldNotContain OffsetDateTime.MAX.toString()
                    slot.captured.subject shouldContain "has a VALID"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                eventDispatcher.dispatch(firstEvent)

                val expectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error2")
                )
                eventDispatcher.dispatch(secondEvent)

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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = SSLValidationError("ssl error1")
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromMonitorEvent(secondEvent)

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
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromMonitorEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "has a VALID"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "has an INVALID"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                val expectedEmail = emailFactory.fromMonitorEvent(event)

                eventDispatcher.dispatch(event)

                then("it should send an email about the event") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.subject shouldContain "will expire soon"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)

                val expectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = OffsetDateTime.MAX),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should send only one email about them") {
                    val slot = slot<Email>()

                    verify(exactly = 1) { mailerSpy.sendAsync(capture(slot)) }
                    slot.captured.plainText shouldBe expectedEmail.plainText
                    slot.captured.plainText shouldNotContain OffsetDateTime.MAX.toString()
                    slot.captured.subject shouldContain "will expire soon"
                    slot.captured.subject shouldBe expectedEmail.subject
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SslEvent.SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromMonitorEvent(secondEvent)

                then("it should send two different emails about them") {
                    val emailsSent = mutableListOf<Email>()

                    verify(exactly = 2) { mailerSpy.sendAsync(capture(emailsSent)) }
                    emailsSent[0].plainText shouldBe firstExpectedEmail.plainText
                    emailsSent[0].subject shouldContain "has a VALID"
                    emailsSent[0].subject shouldBe firstExpectedEmail.subject
                    emailsSent[1].plainText shouldBe secondExpectedEmail.plainText
                    emailsSent[1].subject shouldContain "will expire soon"
                    emailsSent[1].subject shouldBe secondExpectedEmail.subject
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
