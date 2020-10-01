package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest.annotation.MicronautTest

@MicronautTest(startApplication = false)
class UptimeEventRepositoryTest(
    private val monitorRepository: MonitorRepository,
    private val uptimeEventRepository: UptimeEventRepository
) : DatabaseBehaviorSpec() {

    init {
        given("isMonitorUp() method") {
            `when`("the monitor is UP") {
                val monitor = createMonitor(monitorRepository)
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp(),
                    status = UptimeStatus.UP,
                    endedAt = null
                )

                then("it should return true") {
                    uptimeEventRepository.isMonitorUp(monitor.id) shouldBe true
                }
            }

            `when`("the monitor is DOWN") {
                val monitor = createMonitor(monitorRepository)
                createUptimeEventRecord(
                    repository = uptimeEventRepository,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp(),
                    status = UptimeStatus.DOWN,
                    endedAt = null
                )

                then("it should return false") {
                    uptimeEventRepository.isMonitorUp(monitor.id) shouldBe false
                }
            }

            `when`("there is no UPTIME_EVENT record") {
                val monitor = createMonitor(monitorRepository)

                then("it should return false") {
                    uptimeEventRepository.isMonitorUp(monitor.id) shouldBe false
                }
            }
        }
    }
}
