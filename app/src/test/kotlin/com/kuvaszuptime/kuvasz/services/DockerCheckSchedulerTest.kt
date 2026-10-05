package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.UptimeCheckLockRegistry
import com.kuvaszuptime.kuvasz.services.check.docker.DockerCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.docker.DockerUptimeChecker
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
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
import kotlin.time.Duration.Companion.seconds

/**
 * Only covers the Docker-specific wiring of the scheduler, the shared scheduling logic is tested by
 * [UptimeCheckSchedulerTest].
 */
@MicronautTest(startApplication = false)
class DockerCheckSchedulerTest(
    private val checkScheduler: DockerCheckScheduler,
    private val monitorRepository: DockerMonitorRepository,
    private val uptimeChecker: DockerUptimeChecker,
    private val uptimeCheckLockRegistry: UptimeCheckLockRegistry,
) : DatabaseBehaviorSpec() {
    init {
        given("the DockerCheckScheduler service") {
            `when`("there is an enabled monitor in the database and initialize has been called") {
                val monitor = createDockerMonitor(monitorRepository)

                checkScheduler.initialize()

                then("it should schedule the check for it") {
                    with(checkScheduler.getScheduledUptimeChecks()[monitor.id].shouldNotBeNull()) {
                        isCancelled.shouldBeFalse()
                        isDone.shouldBeFalse()
                    }
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                createDockerMonitor(monitorRepository, enabled = false)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledUptimeChecks().shouldBeEmpty()
                }
            }

            `when`("an uptime check is executed") {
                val monitor = createDockerMonitor(monitorRepository, uptimeCheckInterval = 1)
                val uptimeCheckerMock = getMock(uptimeChecker)
                coEvery { uptimeCheckerMock.check(monitor, any()) } just Runs
                val lockRegistryMock = getMock(uptimeCheckLockRegistry)
                coEvery { lockRegistryMock.tryAcquire(monitor.id) } returns true
                coEvery { lockRegistryMock.release(monitor.id) } just Runs

                checkScheduler.initialize()
                eventually(5.seconds) { coVerify(atLeast = 1) { lockRegistryMock.release(monitor.id) } }

                then("it should delegate the check to the Docker uptime checker") {
                    coVerifyOrder {
                        lockRegistryMock.tryAcquire(monitor.id)
                        uptimeCheckerMock.check(monitor, any())
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

    @MockBean(DockerUptimeChecker::class)
    fun uptimeCheckerMock(): DockerUptimeChecker = mockk()

    @MockBean(UptimeCheckLockRegistry::class)
    fun uptimeCheckLockRegistryMock(): UptimeCheckLockRegistry = mockk()

    @MockBean(MaintenanceWindowService::class)
    fun maintenanceWindowServiceMock(): MaintenanceWindowService = mockk {
        every { isUnderMaintenance(any(), any()) } returns false
    }
}
