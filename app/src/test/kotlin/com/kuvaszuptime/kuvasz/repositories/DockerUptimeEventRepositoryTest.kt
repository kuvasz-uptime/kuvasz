package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class DockerUptimeEventRepositoryTest(
    dockerMonitorRepository: DockerMonitorRepository,
    dockerUptimeEventRepository: DockerUptimeEventRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getPreviousEventByMonitorId method") {

            `when`("there are corrupted events in the database") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                val secondRecord = createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = dockerUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    val events = dockerUptimeEventRepository.fetchByMonitorId(monitor.id)

                    previousEvent shouldBe secondRecord
                    events.single() shouldBe secondRecord
                }
            }

            `when`("there is only one open record") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = getCurrentTimestamp().minusDays(2),
                )
                val openEvent = createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = dockerUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    previousEvent shouldBe openEvent
                }
            }
        }

        given("the fetchLatestIncidentTimestamp method") {

            `when`("there isn't any incident") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                )

                then("it should return nothing") {
                    dockerUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe null
                }
            }

            `when`("an ongoing incident was updated after the latest resolved one ended") {
                val now = getCurrentTimestamp()
                val monitor = createDockerMonitor(dockerMonitorRepository)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(5),
                    endedAt = now.minusHours(4),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = null,
                    updatedAt = now.minusMinutes(1),
                )

                then("it should return the last update of the ongoing one") {
                    dockerUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusMinutes(1)
                }
            }

            `when`("the latest resolved incident ended after an ongoing one was last updated") {
                val now = getCurrentTimestamp()
                val ongoingMonitor = createDockerMonitor(dockerMonitorRepository)
                val resolvedMonitor = createDockerMonitor(dockerMonitorRepository)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = ongoingMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = null,
                    updatedAt = now.minusHours(2),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = resolvedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )

                then("it should return the end of the resolved one") {
                    dockerUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusHours(1)
                }
            }

            `when`("the latest events are the ones of a paused monitor, or not incidents") {
                val now = getCurrentTimestamp()
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val pausedMonitor = createDockerMonitor(dockerMonitorRepository, enabled = false)
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(4),
                    endedAt = now.minusHours(3),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusMinutes(10),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusMinutes(10),
                    endedAt = null,
                    updatedAt = now,
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusMinutes(30),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(30),
                    endedAt = null,
                    updatedAt = now.minusMinutes(1),
                )

                then("it should only consider the incidents of the enabled monitors") {
                    dockerUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusHours(3)
                }
            }
        }
    }
}
