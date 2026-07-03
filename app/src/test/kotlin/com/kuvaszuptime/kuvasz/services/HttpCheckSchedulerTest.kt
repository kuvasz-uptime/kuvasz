package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpUptimeChecker
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLChecker
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest(startApplication = false)
class HttpCheckSchedulerTest(
    private val checkScheduler: HttpCheckScheduler,
    private val monitorRepository: HttpMonitorRepository,
    private val uptimeChecker: HttpUptimeChecker,
    private val uptimeCheckLockRegistry: UptimeCheckLockRegistry,
    private val maintenanceWindowService: MaintenanceWindowService,
    private val sslChecker: SSLChecker,
) : DatabaseBehaviorSpec() {
    init {
        given("the CheckScheduler service") {
            `when`("there is an enabled monitor in the database and initialize has been called") {
                val monitor = createHttpMonitor(monitorRepository)

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
                createHttpMonitor(monitorRepository, uptimeCheckInterval = 0)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                createHttpMonitor(monitorRepository, enabled = false)

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
                val monitor = createHttpMonitor(monitorRepository, sslCheckEnabled = false)

                checkScheduler.initialize()

                then("it should schedule only the uptime check for it") {
                    checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                    checkScheduler.getScheduledSSLChecks().shouldBeEmpty()
                }
            }

            `when`("it initializes the uptime checks") {
                val monitor1 =
                    createHttpMonitor(monitorRepository, monitorName = "m1", uptimeCheckInterval = 1000)
                val monitor2 =
                    createHttpMonitor(monitorRepository, monitorName = "m2", uptimeCheckInterval = 30)
                // Make sure that the set-up check won't be rescheduled because of a too fast check invocation
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(any(), any(), any(), any()) } coAnswers { delay(10000.milliseconds) }

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
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), any()) } just Runs
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("it should try to acquire a lock for it & release it afterwards") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }

            `when`("a monitor is under maintenance") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs
                val maintenanceServiceMock = getMock(maintenanceWindowService)
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns true

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("it should skip the check but still acquire and release the lock") {
                    coVerify(atLeast = 1) { lockRegistryMock.tryAcquire(monitor.id) }
                    coVerify(inverse = true) { uptimeCheckerMock.check(any(), any(), any(), any()) }
                    coVerify(atLeast = 1) { lockRegistryMock.release(monitor.id) }
                }
            }

            `when`("an SSL check is executed while the monitor is not under maintenance") {
                val monitor = createHttpMonitor(monitorRepository, sslCheckEnabled = true)
                val sslCheckerMock = getMock(sslChecker)
                every { sslCheckerMock.check(monitor) } just Runs
                val maintenanceServiceMock = getMock(maintenanceWindowService)
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns false

                checkScheduler.runSSLCheck(monitor)

                then("it should run the SSL check without re-scheduling it") {
                    verify(exactly = 1) { sslCheckerMock.check(monitor) }
                    checkScheduler.getScheduledSSLChecks().shouldNotContainKey(monitor.id)
                }
            }

            `when`("an SSL check is executed while the monitor is under maintenance") {
                val monitor = createHttpMonitor(monitorRepository, sslCheckEnabled = true)
                val sslCheckerMock = getMock(sslChecker)
                val maintenanceServiceMock = getMock(maintenanceWindowService)
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns true

                checkScheduler.runSSLCheck(monitor)

                then("it should skip the check and re-schedule it with a ~30 minutes initial delay") {
                    verify(exactly = 1) { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) }
                    verify(inverse = true) { sslCheckerMock.check(any()) }
                    with(checkScheduler.getScheduledSSLChecks()[monitor.id].shouldNotBeNull()) {
                        // 30 minutes = 1800 seconds, allowing a small margin for scheduling overhead
                        getDelay(TimeUnit.SECONDS) shouldBeInRange 1790L..1800L
                    }
                }
            }

            `when`("a re-scheduled SSL check runs after the maintenance is over") {
                val monitor = createHttpMonitor(monitorRepository, sslCheckEnabled = true)
                val sslCheckerMock = getMock(sslChecker)
                every { sslCheckerMock.check(monitor) } just Runs
                val maintenanceServiceMock = getMock(maintenanceWindowService)
                // The check is due while under maintenance, so it gets re-scheduled first
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns true
                checkScheduler.runSSLCheck(monitor)
                // By the time the re-scheduled check fires, the maintenance is over
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns false

                checkScheduler.runSSLCheck(monitor)

                then("it should run the SSL check") {
                    verify(exactly = 1) { sslCheckerMock.check(monitor) }
                }
            }

            `when`("the checks of a monitor with a re-scheduled SSL check are removed") {
                val monitor = createHttpMonitor(monitorRepository, sslCheckEnabled = true)
                val maintenanceServiceMock = getMock(maintenanceWindowService)
                every { maintenanceServiceMock.isUnderMaintenance(monitor.monitorId()) } returns true
                checkScheduler.runSSLCheck(monitor)
                checkScheduler.getScheduledSSLChecks().shouldContainKey(monitor.id)

                checkScheduler.removeChecksOfMonitor(monitor)

                then("the re-scheduled SSL check should be cancelled and removed") {
                    checkScheduler.getScheduledSSLChecks().shouldNotContainKey(monitor.id)
                }
            }

            `when`("a lock can't be acquired for an uptime check") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns false

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("it should not run the check") {
                    coVerify(atLeast = 1) { lockRegistryMock.tryAcquire(monitor.id) }
                    coVerify(inverse = true) { uptimeCheckerMock.check(any(), any(), any(), any()) }
                    coVerify(inverse = true) { lockRegistryMock.release(monitor.id) }
                }
            }

            `when`("an uptime check calls the passed doAfter callback") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), captureLambda()) } coAnswers {
                    lambda<(HttpMonitorRecord) -> Unit>().captured.invoke(monitor)
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
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                    val checkAfter = checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()
                    checkAfter.hashCode() shouldNotBe checkBefore.hashCode()
                }
            }

            `when`("an uptime check throws an exception") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 3)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any(), any(), captureLambda()) } throws Exception("bad")
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                delay(4000.milliseconds) // Wait for the check to be executed

                then("the lock should be released anyway") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any(), any(), any())
                        lockRegistryMock.release(monitor.id)
                    }
                }
            }

            `when`("the getNextCheck() method is called, but no check is scheduled for the given monitor") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 10, sslCheckEnabled = true)
                checkScheduler.initialize()

                then("it should return null") {
                    checkScheduler.getNextCheck(CheckType.UPTIME, monitor.id + 100).shouldBeNull()
                    checkScheduler.getNextCheck(CheckType.SSL, monitor.id + 100).shouldBeNull()
                }
            }

            `when`("the getNextCheck() method is called, and there are scheduled checks for the monitor") {
                val monitor = createHttpMonitor(monitorRepository, uptimeCheckInterval = 100, sslCheckEnabled = true)
                checkScheduler.initialize()

                then("it should return them correctly") {
                    val nextUptimeCheck =
                        checkScheduler.getNextCheck(CheckType.UPTIME, monitor.id).shouldNotBeNull().toEpochSecond()
                    val nextSSLCheck =
                        checkScheduler.getNextCheck(CheckType.SSL, monitor.id).shouldNotBeNull().toEpochSecond()
                    val expectedNextUptimeCheck = checkScheduler
                        .getScheduledUptimeChecks()[monitor.id]
                        .shouldNotBeNull()
                        .getDelay(TimeUnit.SECONDS)
                        .let { Instant.now().epochSecond + it }
                    val expectedNextSSLCheck = checkScheduler
                        .getScheduledSSLChecks()[monitor.id]
                        .shouldNotBeNull()
                        .getDelay(TimeUnit.SECONDS)
                        .let { Instant.now().epochSecond + it }

                    nextUptimeCheck shouldBeInRange expectedNextUptimeCheck - 1..expectedNextUptimeCheck + 1
                    nextSSLCheck shouldBeInRange expectedNextSSLCheck - 1..expectedNextSSLCheck + 1
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }

    @MockBean(HttpUptimeChecker::class)
    fun uptimeCheckerMock(): HttpUptimeChecker = mockk()

    @MockBean(UptimeCheckLockRegistry::class)
    fun uptimeCheckLockRegistryMock(): UptimeCheckLockRegistry = mockk()

    @MockBean(MaintenanceWindowService::class)
    fun maintenanceWindowServiceMock(): MaintenanceWindowService = mockk {
        every { isUnderMaintenance(any()) } returns false
    }

    @MockBean(SSLChecker::class)
    fun sslCheckerMock(): SSLChecker = mockk()
}
