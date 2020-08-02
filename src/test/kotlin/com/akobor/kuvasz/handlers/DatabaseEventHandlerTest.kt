package com.akobor.kuvasz.handlers

import arrow.core.Option
import com.akobor.kuvasz.DatabaseBehaviorSpec
import com.akobor.kuvasz.enums.UptimeStatus
import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.mocks.createMonitor
import com.akobor.kuvasz.repositories.LatencyLogRepository
import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.repositories.UptimeEventRepository
import com.akobor.kuvasz.services.EventDispatcher
import com.akobor.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.akobor.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder

@MicronautTest
class DatabaseEventHandlerTest(
    uptimeEventRepository: UptimeEventRepository,
    latencyLogRepository: LatencyLogRepository,
    monitorRepository: MonitorRepository
) : DatabaseBehaviorSpec() {
    init {
        val eventDispatcher = EventDispatcher()
        val uptimeEventRepositorySpy = spyk(uptimeEventRepository, recordPrivateCalls = true)
        val latencyLogRepositorySpy = spyk(latencyLogRepository, recordPrivateCalls = true)
        DatabaseEventHandler(eventDispatcher, uptimeEventRepositorySpy, latencyLogRepositorySpy)

        given("the DatabaseEventHandler") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status UP and a LatencyLog record") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, event.monitor.id)
                    val expectedLatencyRecord = latencyLogRepository.fetchOne(LATENCY_LOG.MONITOR_ID, event.monitor.id)

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { uptimeEventRepositorySpy.endEventById(any(), any()) }
                    verify(exactly = 1) {
                        latencyLogRepositorySpy.insertLatencyForMonitor(
                            event.monitor.id,
                            event.latency
                        )
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedLatencyRecord.latency shouldBe event.latency
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.empty(),
                    error = Throwable()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status DOWN") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, event.monitor.id)

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { uptimeEventRepositorySpy.endEventById(any(), any()) }

                    expectedUptimeRecord.status shouldBe UptimeStatus.DOWN
                    expectedUptimeRecord.endedAt shouldBe null
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new UptimeEvent record but should create a LatencyLog record") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)
                    val latencyRecords = latencyLogRepository.fetchByMonitorId(monitor.id)
                    latencyRecords.sortBy { it.id }

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent) }
                    verify(exactly = 0) { uptimeEventRepositorySpy.endEventById(any(), any()) }
                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency)
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency)
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.endedAt shouldBe null
                    latencyRecords.size shouldBe 2
                    latencyRecords[0].latency shouldBe firstEvent.latency
                    latencyRecords[1].latency shouldBe secondEvent.latency
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = Option.empty(),
                    error = Throwable()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent record, end the previous one and should create a LatencyLog record") {
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    val latencyRecord = latencyLogRepository.fetchOne(LATENCY_LOG.MONITOR_ID, monitor.id)
                    uptimeRecords.sortBy { it.id }

                    verifyOrder {
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        uptimeEventRepository.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt)
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[0].endedAt shouldBe uptimeRecords[1].startedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.UP
                    uptimeRecords[1].endedAt shouldBe null
                    latencyRecord.latency shouldBe secondEvent.latency
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    previousEvent = Option.empty(),
                    latency = 1000
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchOne(UPTIME_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = Option.just(firstUptimeRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent record and end the previous one") {
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    val latencyRecord = latencyLogRepository.fetchOne(LATENCY_LOG.MONITOR_ID, monitor.id)
                    uptimeRecords.sortBy { it.id }

                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        uptimeEventRepository.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.UP
                    uptimeRecords[0].endedAt shouldBe uptimeRecords[1].startedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[1].endedAt shouldBe null
                    latencyRecord.latency shouldBe firstEvent.latency
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
