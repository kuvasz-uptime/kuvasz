package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.testutils.toSubscriber
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.*
import io.reactivex.subscribers.TestSubscriber
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class SSLCheckerTest(
    private val monitorRepository: MonitorRepository,
    sslEventRepository: SSLEventRepository
) : DatabaseBehaviorSpec() {

    private val sslValidator = mockk<SSLValidator>()
    private val uptimeEventRepository = mockk<UptimeEventRepository>()

    init {
        val eventDispatcher = EventDispatcher()
        val sslChecker = spyk(
            SSLChecker(
                sslValidator = sslValidator,
                eventDispatcher = eventDispatcher,
                sslEventRepository = sslEventRepository,
                uptimeEventRepository = uptimeEventRepository
            )
        )

        given("the SSLChecker service") {
            `when`("it checks a monitor that is DOWN") {
                val monitor = createMonitor(monitorRepository)
                mockIsMonitorUpResult(false)

                sslChecker.check(monitor)

                then("it should not run the SSL check") {
                    verify(exactly = 0) { sslValidator.validate(any()) }
                }
            }

            `when`("it checks a monitor with a valid certificate") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<SSLValidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.toSubscriber(subscriber) }
                mockValidationResult(SslStatus.VALID)
                mockIsMonitorUpResult(true)

                sslChecker.check(monitor)

                then("it should dispatch an SSLValidEvent") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.valueCount() shouldBe 1
                    expectedEvent.monitor.id shouldBe monitor.id
                }
            }

            `when`("it checks a monitor with an INVALID certificate") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLInvalidEvents { it.toSubscriber(subscriber) }
                mockValidationResult(SslStatus.INVALID)
                mockIsMonitorUpResult(true)

                sslChecker.check(monitor)

                then("it should dispatch an SSLInvalidEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()

                    subscriber.valueCount() shouldBe 1
                    expectedEvent.monitor.id shouldBe monitor.id
                    expectedEvent.error.message shouldBe "validation error"
                }
            }

            `when`("it checks a monitor that has an INVALID cert then it's VALID again") {
                val monitor = createMonitor(monitorRepository)
                val certValidSubscriber = TestSubscriber<SSLValidEvent>()
                val certInvalidSubscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.toSubscriber(certValidSubscriber) }
                eventDispatcher.subscribeToSSLInvalidEvents { it.toSubscriber(certInvalidSubscriber) }
                mockValidationResult(SslStatus.INVALID)
                mockIsMonitorUpResult(true)

                then("it should dispatch an SSLInvalid and an SSLValidEvent") {
                    sslChecker.check(monitor)
                    clearMocks(sslValidator)
                    mockValidationResult(SslStatus.VALID)
                    mockIsMonitorUpResult(true)
                    sslChecker.check(monitor)

                    val expectedInvalidEvent = certInvalidSubscriber.values().first()
                    val expectedValidEvent = certValidSubscriber.values().first()

                    certInvalidSubscriber.valueCount() shouldBe 1
                    certValidSubscriber.valueCount() shouldBe 1
                    expectedInvalidEvent.monitor.id shouldBe monitor.id
                    expectedValidEvent.monitor.id shouldBe monitor.id
                    expectedInvalidEvent.dispatchedAt shouldBeLessThan expectedValidEvent.dispatchedAt
                }
            }

            `when`("it checks a monitor that has a VALID cert but then it's INVALID again") {
                val monitor = createMonitor(monitorRepository)
                val certValidSubscriber = TestSubscriber<SSLValidEvent>()
                val certInvalidSubscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.toSubscriber(certValidSubscriber) }
                eventDispatcher.subscribeToSSLInvalidEvents { it.toSubscriber(certInvalidSubscriber) }
                mockValidationResult(SslStatus.VALID)
                mockIsMonitorUpResult(true)

                then("it should dispatch an SSLValid and then an SSLInvalidEvent") {
                    sslChecker.check(monitor)
                    clearMocks(sslValidator)
                    mockValidationResult(SslStatus.INVALID)
                    mockIsMonitorUpResult(true)
                    sslChecker.check(monitor)

                    val expectedInvalidEvent = certInvalidSubscriber.values().first()
                    val expectedValidEvent = certValidSubscriber.values().first()

                    certInvalidSubscriber.valueCount() shouldBe 1
                    certValidSubscriber.valueCount() shouldBe 1
                    expectedInvalidEvent.monitor.id shouldBe monitor.id
                    expectedValidEvent.monitor.id shouldBe monitor.id
                    expectedInvalidEvent.dispatchedAt shouldBeGreaterThan expectedValidEvent.dispatchedAt
                }
            }

            `when`("it checks a monitor that has a cert that expires soon") {
                val monitor = createMonitor(monitorRepository)
                val subscriber = TestSubscriber<SSLWillExpireEvent>()
                eventDispatcher.subscribeToSSLWillExpireEvents { it.toSubscriber(subscriber) }
                val validTo = getCurrentTimestamp().minusDays(29)
                mockValidationResult(
                    status = SslStatus.WILL_EXPIRE,
                    validTo = validTo
                )
                mockIsMonitorUpResult(true)

                sslChecker.check(monitor)

                then("it should dispatch an SSLWillExpireEvent with the right expiration date") {
                    val expectedEvent = subscriber.values().first()

                    subscriber.valueCount() shouldBe 1
                    expectedEvent.monitor.id shouldBe monitor.id
                    expectedEvent.certInfo.validTo shouldBe validTo
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearMocks(sslValidator)
        super.afterTest(testCase, result)
    }

    private fun mockValidationResult(
        status: SslStatus,
        validTo: OffsetDateTime = getCurrentTimestamp().plusDays(60)
    ) {
        val certInfo = CertificateInfo(validTo)
        val mockResult: Either<SSLValidationError, CertificateInfo> = when (status) {
            SslStatus.VALID -> Either.Right(certInfo)
            SslStatus.WILL_EXPIRE -> Either.Right(certInfo)
            SslStatus.INVALID -> Either.Left(SSLValidationError("validation error"))
        }
        every { sslValidator.validate(any()) } returns mockResult
    }

    private fun mockIsMonitorUpResult(result: Boolean) {
        every { uptimeEventRepository.isMonitorUp(any()) } returns result
    }
}
