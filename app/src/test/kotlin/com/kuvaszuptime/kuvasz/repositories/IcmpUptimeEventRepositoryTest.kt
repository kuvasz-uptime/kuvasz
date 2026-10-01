package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class IcmpUptimeEventRepositoryTest(
    icmpMonitorRepository: IcmpMonitorRepository,
    icmpUptimeEventRepository: IcmpUptimeEventRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getPreviousEventByMonitorId method") {

            `when`("there are corrupted events in the database") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                val secondRecord = createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = icmpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    val events = icmpUptimeEventRepository.fetchByMonitorId(monitor.id)

                    previousEvent shouldBe secondRecord
                    events.single() shouldBe secondRecord
                }
            }

            `when`("there is only one open record") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = getCurrentTimestamp().minusDays(2),
                )
                val openEvent = createIcmpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = icmpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    previousEvent shouldBe openEvent
                }
            }
        }
    }
}
