package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class UptimeEventRepositoryTest(
    private val monitorRepository: HttpMonitorRepository,
    private val uptimeEventRepository: HttpUptimeEventRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("isMonitorUp() method") {
            `when`("the monitor is UP") {
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
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
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    startedAt = getCurrentTimestamp(),
                    status = UptimeStatus.DOWN,
                    endedAt = null
                )

                then("it should return false") {
                    uptimeEventRepository.isMonitorUp(monitor.id) shouldBe false
                }
            }

            `when`("there is no HTTP_UPTIME_EVENT record") {
                val monitor = createHttpMonitor(monitorRepository)

                then("it should return false") {
                    uptimeEventRepository.isMonitorUp(monitor.id) shouldBe false
                }
            }
        }
    }
}
