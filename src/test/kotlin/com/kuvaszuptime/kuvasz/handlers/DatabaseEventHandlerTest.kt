package com.kuvaszuptime.kuvasz.handlers

import arrow.core.Option
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldHaveSize
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
    monitorRepository: MonitorRepository,
    sslEventRepository: SSLEventRepository
) : DatabaseBehaviorSpec() {
    init {
        val eventDispatcher = EventDispatcher()
        val uptimeEventRepositorySpy = spyk(uptimeEventRepository)
        val latencyLogRepositorySpy = spyk(latencyLogRepository)
        val sslEventRepositorySpy = spyk(sslEventRepository)
        DatabaseEventHandler(eventDispatcher, uptimeEventRepositorySpy, latencyLogRepositorySpy, sslEventRepositorySpy)

        given("the DatabaseEventHandler - UPTIME events") {
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
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
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
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
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
                    val latencyRecords = latencyLogRepository.fetchByMonitorId(monitor.id).sortedBy { it.createdAt }

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent) }
                    verify(exactly = 0) { uptimeEventRepositorySpy.endEventById(any(), any()) }
                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency)
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency)
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe secondEvent.dispatchedAt
                    latencyRecords shouldHaveSize 2
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

                then("it should create a new UptimeEvent and a LatencyLog record, and end the previous one") {
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchOne(LATENCY_LOG.MONITOR_ID, monitor.id)

                    verifyOrder {
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency)
                        uptimeEventRepositorySpy.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.UP
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
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
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchOne(LATENCY_LOG.MONITOR_ID, monitor.id)

                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        uptimeEventRepositorySpy.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.UP
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    latencyRecord.latency shouldBe firstEvent.latency
                }
            }
        }

        given("the DatabaseEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status VALID") {
                    val expectedSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, event.monitor.id)

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any()) }

                    expectedSSLRecord.status shouldBe SslStatus.VALID
                    expectedSSLRecord.startedAt shouldBe event.dispatchedAt
                    expectedSSLRecord.endedAt shouldBe null
                    expectedSSLRecord.updatedAt shouldBe event.dispatchedAt
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.empty(),
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status INVALID") {
                    val expectedSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, event.monitor.id)

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any()) }

                    expectedSSLRecord.status shouldBe SslStatus.INVALID
                    expectedSSLRecord.startedAt shouldBe event.dispatchedAt
                    expectedSSLRecord.endedAt shouldBe null
                    expectedSSLRecord.updatedAt shouldBe event.dispatchedAt
                    expectedSSLRecord.error shouldBe "ssl error"
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new SSLEvent record") {
                    val expectedSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(firstEvent) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any()) }

                    expectedSSLRecord.status shouldBe SslStatus.VALID
                    expectedSSLRecord.endedAt shouldBe null
                    expectedSSLRecord.updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.empty(),
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record, and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt)
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    sslRecords[0].status shouldBe SslStatus.INVALID
                    sslRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].status shouldBe SslStatus.VALID
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = Option.just(firstSSLRecord),
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt)
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    sslRecords[0].status shouldBe SslStatus.VALID
                    sslRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].status shouldBe SslStatus.INVALID
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status WILL_EXPIRE") {
                    val expectedSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, event.monitor.id)

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any()) }

                    expectedSSLRecord.status shouldBe SslStatus.WILL_EXPIRE
                    expectedSSLRecord.startedAt shouldBe event.dispatchedAt
                    expectedSSLRecord.endedAt shouldBe null
                    expectedSSLRecord.updatedAt shouldBe event.dispatchedAt
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new SSLEvent record") {
                    val expectedSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(firstEvent) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any()) }

                    expectedSSLRecord.status shouldBe SslStatus.WILL_EXPIRE
                    expectedSSLRecord.endedAt shouldBe null
                    expectedSSLRecord.updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createMonitor(monitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.empty()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchOne(SSL_EVENT.MONITOR_ID, monitor.id)

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = Option.just(firstSSLRecord)
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record, and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent)
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt)
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent)
                    }

                    sslRecords[0].status shouldBe SslStatus.VALID
                    sslRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].status shouldBe SslStatus.WILL_EXPIRE
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                }
            }
        }
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
