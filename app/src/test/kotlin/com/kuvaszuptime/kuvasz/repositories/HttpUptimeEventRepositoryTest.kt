package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class HttpUptimeEventRepositoryTest(
    httpMonitorRepository: HttpMonitorRepository,
    httpUptimeEventRepository: HttpUptimeEventRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getPreviousEventByMonitorId method") {

            `when`("there are corrupted events in the database") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                val secondRecord = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = httpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    val events = httpUptimeEventRepository.fetchByMonitorId(monitor.id)

                    previousEvent shouldBe secondRecord
                    events.single() shouldBe secondRecord
                }
            }

            `when`("there is only one open record") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = getCurrentTimestamp().minusDays(2),
                )
                val openEvent = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = httpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    previousEvent shouldBe openEvent
                }
            }
        }
    }
}
