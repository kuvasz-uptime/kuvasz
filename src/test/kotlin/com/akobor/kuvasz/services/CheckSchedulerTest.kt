package com.akobor.kuvasz.services

import com.akobor.kuvasz.DatabaseBehaviorSpec
import com.akobor.kuvasz.mocks.createMonitor
import com.akobor.kuvasz.repositories.MonitorRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
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
                    val expectedCheck = checkScheduler.getScheduledChecks().find { it.monitorId == monitor.id }
                    expectedCheck shouldNotBe null
                    expectedCheck!!.task.isCancelled shouldBe false
                    expectedCheck.task.isDone shouldBe false
                }
            }

            `when`("there is an enabled but unschedulable monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository, id = 88888, uptimeCheckInterval = 0)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledChecks().any { it.monitorId == monitor.id } shouldBe false
                }
            }

            `when`("there is a disabled monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository, id = 11111, enabled = false)

                checkScheduler.initialize()

                then("it should not schedule the check for it") {
                    checkScheduler.getScheduledChecks().any { it.monitorId == monitor.id } shouldBe false
                }
            }
        }
    }
}
