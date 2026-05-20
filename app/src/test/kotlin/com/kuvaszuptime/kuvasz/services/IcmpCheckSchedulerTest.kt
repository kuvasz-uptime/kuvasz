package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpUptimeChecker
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
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
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest(startApplication = false)
class IcmpCheckSchedulerTest(
    private val checkScheduler: IcmpCheckScheduler,
    private val monitorRepository: IcmpMonitorRepository,
    private val uptimeChecker: IcmpUptimeChecker,
    private val uptimeCheckLockRegistry: UptimeCheckLockRegistry,
) : DatabaseBehaviorSpec() {
    init {
        given("the IcmpCheckScheduler service") {
            `when`("there is an enabled monitor in the database and initialize has been called") {
                val monitor = createIcmpMonitor(monitorRepository)

                checkScheduler.initialize()

                then("it should schedule the check for it") {
                    with(checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()) {
                        isCancelled.shouldBeFalse()
                        isDone.shouldBeFalse()
                    }
                }
            }

            `when`("there is an enabled but unschedulable monitor in the database and initialize has been called") {
                createIcmpMonitor(monitorRepository, uptimeCheckInterval = 0)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                createIcmpMonitor(monitorRepository, enabled = false)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                }
            }

            `when`("it initializes the uptime checks") {
                val monitor1 = createIcmpMonitor(monitorRepository, monitorName = "m1", uptimeCheckInterval = 1000)
                val monitor2 = createIcmpMonitor(monitorRepository, monitorName = "m2", uptimeCheckInterval = 30)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(any(), any()) } coAnswers { delay(10000.milliseconds) }

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
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any()) } just Runs
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("it should try to acquire a lock for it & release it afterwards") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }

            `when`("a lock can't be acquired for an uptime check") {
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns false

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("it should not run the check") {
                    coVerify(atLeast = 1) { lockRegistryMock.tryAcquire(monitor.id) }
                    coVerify(inverse = true) { uptimeCheckerMock.check(any(), any()) }
                    coVerify(inverse = true) { lockRegistryMock.release(monitor.id) }
                }
            }

            `when`("an uptime check calls the passed doAfter callback") {
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, captureLambda()) } coAnswers {
                    lambda<(IcmpMonitorRecord) -> Unit>().captured.invoke(monitor)
                }
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                val checkBefore = checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("the next check should be re-scheduled via the check's callback") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any())
                        lockRegistryMock.release(monitor.id)
                    }
                    val checkAfter = checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                    checkAfter.hashCode() shouldNotBe checkBefore.hashCode()
                }
            }

            `when`("an uptime check throws an exception") {
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any()) } throws Exception("bad")
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("the lock should be released anyway") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }

            `when`("the getNextCheck() method is called, but no check is scheduled for the given monitor") {
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 10)
                checkScheduler.initialize()

                then("it should return null") {
                    checkScheduler.getNextCheck(monitor.id + 100).shouldBeNull()
                }
            }

            `when`("the getNextCheck() method is called, and there are scheduled checks for the monitor") {
                val monitor = createIcmpMonitor(monitorRepository, uptimeCheckInterval = 100)
                checkScheduler.initialize()

                then("it should return the next check correctly") {
                    checkScheduler.getNextCheck(monitor.id).shouldNotBeNull()
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }

    @MockBean(IcmpUptimeChecker::class)
    fun uptimeCheckerMock(): IcmpUptimeChecker = mockk()

    @MockBean(UptimeCheckLockRegistry::class)
    fun uptimeCheckLockRegistryMock(): UptimeCheckLockRegistry = mockk()
}
