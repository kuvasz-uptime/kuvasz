package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest.annotation.MicronautTest

@MicronautTest(startApplication = false)
class CheckSchedulerTest(
    private val checkScheduler: CheckScheduler,
    private val monitorRepository: MonitorRepository
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
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        checkScheduler.removeAllChecks()
        super.afterTest(testCase, result)
    }
}
