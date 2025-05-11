package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = false)
class CheckSchedulerTest(
    private val checkScheduler: CheckScheduler,
    private val monitorRepository: MonitorRepository,
    private val uptimeChecker: UptimeChecker,
    private val uptimeCheckLockRegistry: UptimeCheckLockRegistry,
) : DatabaseBehaviorSpec() {
    init {
        given("the CheckScheduler service") {
            `when`("there is an enabled monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository)

                checkScheduler.initialize()

                then("it should schedule the check for it") {
                    with(checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()) {
                        isCancelled.shouldBeFalse()
                        isDone.shouldBeFalse()
                    }
                    with(checkScheduler.getScheduledSSLChecks()[monitor.id].shouldNotBeNull()) {
                        isCancelled.shouldBeFalse()
                        isDone.shouldBeFalse()
                    }
                }
            }

            `when`("there is an enabled but unschedulable monitor in the database and initialize has been called") {
                createMonitor(monitorRepository, uptimeCheckInterval = 0)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                createMonitor(monitorRepository, enabled = false)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`(
                "there is an enabled monitor in the database with disabled SSL checks" +
                    " and initialize has been called"
            ) {
                val monitor = createMonitor(monitorRepository, sslCheckEnabled = false)

                checkScheduler.initialize()

                then("it should schedule only the uptime check for it") {
                    checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("it initializes the uptime checks") {
                val monitor1 =
                    createMonitor(monitorRepository, monitorName = "m1", uptimeCheckInterval = 1000)
                val monitor2 =
                    createMonitor(monitorRepository, monitorName = "m2", uptimeCheckInterval = 30)
                // Make sure that the set-up check won't be rescheduled because of a too fast check invocation
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(any(), any(), any(), any()) } coAnswers { delay(10000) }

                checkScheduler.initialize()

                then("it should spread the first checks a little bit") {
                    with(checkScheduler.getScheduledUptimeChecks()[monitor1.id].shouldNotBeNull()) {
                        getDelay(TimeUnit.SECONDS) shouldBeInRange 0L..1000
                    }
                    with(checkScheduler.getScheduledUptimeChecks()[monitor2.id].shouldNotBeNull()) {
                        getDelay(TimeUnit.SECONDS) shouldBeInRange 0L..30
                    }
                }
            }

            `when`("an uptime check is executed") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), any()) } just Runs
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000) // Wait for the check to be executed

                then("it should try to acquire a lock for it & release it afterwards") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }

            `when`("a lock can't be acquired for an uptime check") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns false

                checkScheduler.initialize()
                delay(4000) // Wait for the check to be executed

                then("it should not run the check") {
                    coVerify(atLeast = 1) { lockRegistryMock.tryAcquire(monitor.id) }
                    coVerify(inverse = true) { uptimeCheckerMock.check(any(), any(), any(), any()) }
                    coVerify(inverse = true) { lockRegistryMock.release(monitor.id) }
                }
            }

            `when`("an uptime check calls the passed doAfter callback") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), captureLambda()) } coAnswers {
                    lambda<(MonitorRecord) -> Unit>().captured.invoke(monitor)
                }
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                val checkBefore = checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                delay(4000) // Wait for the check to be executed

                then("the next check should be re-scheduled via the check's callback") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                    val checkAfter = checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                    checkAfter.hashCode() shouldNotBe checkBefore.hashCode()
                }
            }

            `when`("an uptime check throws an exception") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), captureLambda()) } throws Exception("bad")
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000) // Wait for the check to be executed

                then("the lock should be released anyway") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }

    @MockBean(UptimeChecker::class)
    fun uptimeCheckerMock(): UptimeChecker = mockk()

    @MockBean(UptimeCheckLockRegistry::class)
    fun uptimeCheckLockRegistryMock(): UptimeCheckLockRegistry = mockk()
}
