package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.*
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
                    val expectedChecks = checkScheduler.getScheduledChecks().filter { it.monitorId == monitor.id }
                    expectedChecks shouldHaveSize 2
                    expectedChecks.forOne { it.checkType shouldBe CheckType.UPTIME }
                    expectedChecks.forExactly(2) { it.task.isCancelled shouldBe false }
                    expectedChecks.forExactly(2) { it.task.isDone shouldBe false }
                }
            }

            `when`("there is an enabled but unschedulable monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository, id = 88888, uptimeCheckInterval = 0)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe monitor.id }
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository, id = 11111, enabled = false)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledChecks().forNone { it.monitorId shouldBe monitor.id }
                }
            }

            `when`(
                "there is an enabled monitor in the database with disabled SSL checks" +
                    " and initialize has been called"
            ) {
                val monitor = createMonitor(monitorRepository, sslCheckEnabled = false)

                checkScheduler.initialize()

                then("it should schedule only the uptime check for it") {
                    val checks = checkScheduler.getScheduledChecks().filter { it.monitorId == monitor.id }
                    checks shouldHaveSize 1
                    checks[0].checkType shouldBe CheckType.UPTIME
                }
            }

            `when`("it initializes the uptime checks") {
                val monitor1 =
                    createMonitor(monitorRepository, id = 22222, monitorName = "m1", uptimeCheckInterval = 1000)
                val monitor2 =
                    createMonitor(monitorRepository, id = 33333, monitorName = "m2", uptimeCheckInterval = 30)
                // Make sure that the set up check won't be rescheduled because of a too fast check invocation
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(any(), any(), any()) } coAnswers { delay(10000) }

                checkScheduler.initialize()

                then("it should spread the first checks a little bit") {
                    val expectedChecks = checkScheduler.getScheduledChecks().filter { it.checkType == CheckType.UPTIME }
                    expectedChecks shouldHaveSize 2
                    expectedChecks.forExactly(1) { firstMonitor ->
                        firstMonitor.task.getDelay(TimeUnit.SECONDS) shouldBeInRange 0L..1000
                        firstMonitor.monitorId shouldBe monitor1.id
                    }
                    expectedChecks.forExactly(1) { secondMonitor ->
                        secondMonitor.task.getDelay(TimeUnit.SECONDS) shouldBeInRange 0L..30
                        secondMonitor.monitorId shouldBe monitor2.id
                    }
                }
            }

            `when`("an uptime check is executed") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any()) } just Runs
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000) // Wait for the check to be executed

                then("it should try to acquire a lock for it & release it afterwards") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any())
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
                    coVerify(inverse = true) { uptimeCheckerMock.check(any(), any(), any()) }
                    coVerify(inverse = true) { lockRegistryMock.release(monitor.id) }
                }
            }

            `when`("an uptime check calls the passed doAfter callback") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), captureLambda()) } coAnswers {
                    lambda<(MonitorRecord) -> Unit>().captured.invoke(monitor)
                }
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                val checkBefore = checkScheduler.getScheduledChecks().single { it.checkType == CheckType.UPTIME }
                delay(4000) // Wait for the check to be executed

                then("the next check should be re-scheduled via the check's callback") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                    val checkAfter = checkScheduler.getScheduledChecks().single { it.checkType == CheckType.UPTIME }
                    checkAfter.hashCode() shouldNotBe checkBefore.hashCode()
                    checkAfter.monitorId shouldBe checkBefore.monitorId
                }
            }

            `when`("an uptime check throws an exception") {
                val monitor = createMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), captureLambda()) } throws Throwable("bad")
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000) // Wait for the check to be executed

                then("the lock should be released anyway") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any())
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
