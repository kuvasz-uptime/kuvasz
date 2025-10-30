package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class PushUptimeEventRepositoryTest(
    pushMonitorRepository: PushMonitorRepository,
    pushUptimeEventRepository: PushUptimeEventRepository,
    dslContext: DSLContext,
) : DatabaseBehaviorSpec({

    given("the getPreviousEventByMonitorId method") {

        `when`("there are corrupted events in the database") {
            val monitor = createPushMonitor(pushMonitorRepository)
            createPushUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(3),
                endedAt = null,
            )
            val secondRecord = createPushUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(3).plusSeconds(1),
                endedAt = null,
            )

            then("it should delete the irrelevant records and only return the latest one") {

                val previousEvent = pushUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                val events = pushUptimeEventRepository.fetchByMonitorId(monitor.id)

                previousEvent shouldBe secondRecord
                events.single() shouldBe secondRecord
            }
        }

        `when`("there is only one open record") {
            val monitor = createPushMonitor(pushMonitorRepository)
            createPushUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp().minusDays(3),
                endedAt = getCurrentTimestamp().minusDays(2),
            )
            val openEvent = createPushUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = getCurrentTimestamp().minusDays(2),
                endedAt = null,
            )

            then("it should delete the irrelevant records and only return the latest one") {

                val previousEvent = pushUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                previousEvent shouldBe openEvent
            }
        }
    }
})
