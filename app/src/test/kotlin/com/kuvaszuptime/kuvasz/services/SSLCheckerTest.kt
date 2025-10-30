package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLChecker
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLValidator
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import io.reactivex.rxjava3.subscribers.TestSubscriber
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class SSLCheckerTest(
    private val monitorRepository: HttpMonitorRepository,
    sslEventRepository: SSLEventRepository
) : DatabaseBehaviorSpec() {

    private val sslValidator = mockk<SSLValidator>()

    init {
        val eventDispatcher = EventDispatcher()
        val mockDbEventHandler = mockk<DatabaseEventHandler>(relaxed = true)
        val sslChecker = spyk(
            SSLChecker(
                sslValidator = sslValidator,
                eventDispatcher = eventDispatcher,
                sslEventRepository = sslEventRepository,
                databaseEventHandler = mockDbEventHandler,
            )
        )

        given("the SSLChecker service") {

            `when`("it checks a monitor with a valid certificate") {
                val monitor = createHttpMonitor(monitorRepository)
                val subscriber = TestSubscriber<SSLValidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.forwardToSubscriber(subscriber) }
                mockValidationResult(SslStatus.VALID)

                sslChecker.check(monitor)

                then("it should dispatch an SSLValidEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()
                    expectedEvent.monitor.id shouldBe monitor.id

                    verify { mockDbEventHandler.handleSSLMonitorEvent(expectedEvent) }
                }
            }

            `when`("it checks a monitor with an INVALID certificate") {
                val monitor = createHttpMonitor(monitorRepository)
                val subscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLInvalidEvents { it.forwardToSubscriber(subscriber) }
                mockValidationResult(SslStatus.INVALID)

                sslChecker.check(monitor)

                then("it should dispatch an SSLInvalidEvent") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()

                    expectedEvent.monitor.id shouldBe monitor.id
                    expectedEvent.error.message shouldBe "validation error"

                    verify { mockDbEventHandler.handleSSLMonitorEvent(expectedEvent) }
                }
            }

            `when`("it checks a monitor that has an INVALID cert then it's VALID again") {
                val monitor = createHttpMonitor(monitorRepository)
                val certValidSubscriber = TestSubscriber<SSLValidEvent>()
                val certInvalidSubscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.forwardToSubscriber(certValidSubscriber) }
                eventDispatcher.subscribeToSSLInvalidEvents { it.forwardToSubscriber(certInvalidSubscriber) }
                mockValidationResult(SslStatus.INVALID)

                then("it should dispatch an SSLInvalid and an SSLValidEvent") {
                    sslChecker.check(monitor)
                    clearMocks(sslValidator)
                    mockValidationResult(SslStatus.VALID)
                    sslChecker.check(monitor)

                    val expectedInvalidEvent = certInvalidSubscriber.awaitCount(1).values().first()
                    val expectedValidEvent = certValidSubscriber.awaitCount(1).values().first()

                    expectedInvalidEvent.monitor.id shouldBe monitor.id
                    expectedValidEvent.monitor.id shouldBe monitor.id
                    expectedInvalidEvent.dispatchedAt shouldBeLessThan expectedValidEvent.dispatchedAt

                    verifyOrder {
                        mockDbEventHandler.handleSSLMonitorEvent(expectedInvalidEvent)
                        mockDbEventHandler.handleSSLMonitorEvent(expectedValidEvent)
                    }
                }
            }

            `when`("it checks a monitor that has a VALID cert but then it's INVALID again") {
                val monitor = createHttpMonitor(monitorRepository)
                val certValidSubscriber = TestSubscriber<SSLValidEvent>()
                val certInvalidSubscriber = TestSubscriber<SSLInvalidEvent>()
                eventDispatcher.subscribeToSSLValidEvents { it.forwardToSubscriber(certValidSubscriber) }
                eventDispatcher.subscribeToSSLInvalidEvents { it.forwardToSubscriber(certInvalidSubscriber) }
                mockValidationResult(SslStatus.VALID)

                then("it should dispatch an SSLValid and then an SSLInvalidEvent") {
                    sslChecker.check(monitor)
                    clearMocks(sslValidator)
                    mockValidationResult(SslStatus.INVALID)
                    sslChecker.check(monitor)

                    val expectedInvalidEvent = certInvalidSubscriber.awaitCount(1).values().first()
                    val expectedValidEvent = certValidSubscriber.awaitCount(1).values().first()

                    expectedInvalidEvent.monitor.id shouldBe monitor.id
                    expectedValidEvent.monitor.id shouldBe monitor.id
                    expectedInvalidEvent.dispatchedAt shouldBeGreaterThan expectedValidEvent.dispatchedAt

                    verifyOrder {
                        mockDbEventHandler.handleSSLMonitorEvent(expectedValidEvent)
                        mockDbEventHandler.handleSSLMonitorEvent(expectedInvalidEvent)
                    }
                }
            }

            `when`("it checks a monitor that has a cert that expires soon") {
                val monitor = createHttpMonitor(monitorRepository, sslExpiryThreshold = 15)
                val subscriber = TestSubscriber<SSLWillExpireEvent>()
                eventDispatcher.subscribeToSSLWillExpireEvents { it.forwardToSubscriber(subscriber) }
                val validTo = getCurrentTimestamp().plusDays(14)
                mockValidationResult(
                    status = SslStatus.WILL_EXPIRE,
                    validTo = validTo
                )

                sslChecker.check(monitor)

                then("it should dispatch an SSLWillExpireEvent with the right expiration date") {
                    val expectedEvent = subscriber.awaitCount(1).values().first()

                    expectedEvent.monitor.id shouldBe monitor.id
                    expectedEvent.certInfo.validTo shouldBe validTo

                    verify { mockDbEventHandler.handleSSLMonitorEvent(expectedEvent) }
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
        every { sslValidator.validateHttps(any()) } returns mockResult
    }
}
