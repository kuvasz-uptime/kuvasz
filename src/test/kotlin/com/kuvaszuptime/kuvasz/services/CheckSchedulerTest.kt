package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.kotest.inspectors.forNone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class CheckSchedulerTest(
    private val checkScheduler: CheckScheduler,
    private val monitorRepository: MonitorRepository
) : DatabaseBehaviorSpec(
    {
        given("the CheckScheduler service") {
            `when`("there is an enabled monitor in the database and initialize has been called") {
                val monitor = createMonitor(monitorRepository)

                checkScheduler.initialize()

                then("it should schedule the check for it") {
                    val expectedCheck = checkScheduler.getScheduledChecks().find { it.monitorId == monitor.id }
                    expectedCheck shouldNotBe null
                    expectedCheck!!.checkType shouldBe CheckType.UPTIME
                    expectedCheck.task.isCancelled shouldBe false
                    expectedCheck.task.isDone shouldBe false
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
        }
    }
)
