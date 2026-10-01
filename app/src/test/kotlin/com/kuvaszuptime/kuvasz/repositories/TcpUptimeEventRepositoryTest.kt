package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class TcpUptimeEventRepositoryTest(
    tcpMonitorRepository: TcpMonitorRepository,
    tcpUptimeEventRepository: TcpUptimeEventRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getPreviousEventByMonitorId method") {

            `when`("there are corrupted events in the database") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                val secondRecord = createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3).plusSeconds(1),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = tcpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    val events = tcpUptimeEventRepository.fetchByMonitorId(monitor.id)

                    previousEvent shouldBe secondRecord
                    events.single() shouldBe secondRecord
                }
            }

            `when`("there is only one open record") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = getCurrentTimestamp().minusDays(2),
                )
                val openEvent = createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                )

                then("it should delete the irrelevant records and only return the latest one") {

                    val previousEvent = tcpUptimeEventRepository.getPreviousEventByMonitorId(monitor.id)
                    previousEvent shouldBe openEvent
                }
            }
        }

        given("the fetchLatestIncidentTimestamp method") {

            `when`("there isn't any incident") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                )

                then("it should return nothing") {
                    tcpUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe null
                }
            }

            `when`("an ongoing incident was updated after the latest resolved one ended") {
                val now = getCurrentTimestamp()
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(5),
                    endedAt = now.minusHours(4),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = null,
                    updatedAt = now.minusMinutes(1),
                )

                then("it should return the last update of the ongoing one") {
                    tcpUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusMinutes(1)
                }
            }

            `when`("the latest resolved incident ended after an ongoing one was last updated") {
                val now = getCurrentTimestamp()
                val ongoingMonitor = createTcpMonitor(tcpMonitorRepository)
                val resolvedMonitor = createTcpMonitor(tcpMonitorRepository)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = ongoingMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = null,
                    updatedAt = now.minusHours(2),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = resolvedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )

                then("it should return the end of the resolved one") {
                    tcpUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusHours(1)
                }
            }

            `when`("the latest events are the ones of a paused monitor, or not incidents") {
                val now = getCurrentTimestamp()
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val pausedMonitor = createTcpMonitor(tcpMonitorRepository, enabled = false)
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(4),
                    endedAt = now.minusHours(3),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusMinutes(10),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusMinutes(10),
                    endedAt = null,
                    updatedAt = now,
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusMinutes(30),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(30),
                    endedAt = null,
                    updatedAt = now.minusMinutes(1),
                )

                then("it should only consider the incidents of the enabled monitors") {
                    tcpUptimeEventRepository.fetchLatestIncidentTimestamp() shouldBe now.minusHours(3)
                }
            }
        }
    }
}
