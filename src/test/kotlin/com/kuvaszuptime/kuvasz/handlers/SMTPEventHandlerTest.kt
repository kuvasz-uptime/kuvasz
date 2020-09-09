package com.kuvaszuptime.kuvasz.handlers

import arrow.core.Option
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.handlers.SMTPEventHandlerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SMTPMailer
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.simplejavamail.api.email.Email

@MicronautTest
@Property(name = "handler-config.smtp-event-handler.enabled", value = "true")
class SMTPEventHandlerTest(
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository,
    smtpEventHandlerConfig: SMTPEventHandlerConfig,
    smtpMailer: SMTPMailer

) : DatabaseBehaviorSpec() {
    init {
        val emailFactory = EmailFactory(smtpEventHandlerConfig)
        val mailerSpy = spyk(smtpMailer, recordPrivateCalls = true)
        SMTPEventHandler(smtpEventHandlerConfig, mailerSpy, eventDispatcher)

        given("the SMTPEventHandler") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                val expectedEmail = emailFactory.fromUptimeMonitorEvent(event)

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
                    previousEvent = Option.empty()
                )
                val expectedEmail = emailFactory.fromUptimeMonitorEvent(event)

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
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)
                val expectedEmail = emailFactory.fromUptimeMonitorEvent(firstEvent)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = Option.just(firstUptimeRecord)
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
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)
                val expectedEmail = emailFactory.fromUptimeMonitorEvent(firstEvent)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable("Second error"),
                    previousEvent = Option.just(firstUptimeRecord)
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
                    previousEvent = Option.empty(),
                    error = Throwable()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromUptimeMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromUptimeMonitorEvent(secondEvent)

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
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.just(firstUptimeRecord),
                    error = Throwable()
                )
                eventDispatcher.dispatch(secondEvent)

                val firstExpectedEmail = emailFactory.fromUptimeMonitorEvent(firstEvent)
                val secondExpectedEmail = emailFactory.fromUptimeMonitorEvent(secondEvent)

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
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
