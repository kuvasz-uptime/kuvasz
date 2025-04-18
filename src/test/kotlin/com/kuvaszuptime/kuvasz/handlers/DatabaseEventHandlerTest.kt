package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.models.events.*
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class DatabaseEventHandlerTest(
    uptimeEventRepository: UptimeEventRepository,
    latencyLogRepository: LatencyLogRepository,
    monitorRepository: MonitorRepository,
    sslEventRepository: SSLEventRepository,
    dslContext: DSLContext,
) : DatabaseBehaviorSpec() {
    init {
        val eventDispatcher = EventDispatcher()
        val uptimeEventRepositorySpy = spyk(uptimeEventRepository)
        val latencyLogRepositorySpy = spyk(latencyLogRepository)
        val sslEventRepositorySpy = spyk(sslEventRepository)
        DatabaseEventHandler(
            eventDispatcher,
            uptimeEventRepositorySpy,
            latencyLogRepositorySpy,
            sslEventRepositorySpy,
            dslContext,
        )

        given("the DatabaseEventHandler - UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status UP and a LatencyLog record") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchByMonitorId(event.monitor.id).single()
                    val expectedLatencyRecord = latencyLogRepository.fetchByMonitorId(event.monitor.id).single()

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

            `when`("it receives a MonitorUpEvent and latency history is disabled") {

                val monitor = createMonitor(monitorRepository, latencyHistoryEnabled = false)
                val event = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should NOT save the latency log record") {

                    uptimeEventRepository.fetchByMonitorId(event.monitor.id).single()
                    latencyLogRepository.fetchByMonitorId(monitor.id).shouldBeEmpty()

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(event) }
                    verify(exactly = 0) { latencyLogRepositorySpy.insertLatencyForMonitor(any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Throwable()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status DOWN") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

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
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new UptimeEvent record but should create a LatencyLog record") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()
                    val latencyRecords = latencyLogRepository.fetchByMonitorId(monitor.id).sortedBy { it.createdAt }

                    verify(exactly = 1) { uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any()) }
                    verify(exactly = 0) { uptimeEventRepositorySpy.endEventById(any(), any(), any()) }
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
                    previousEvent = null,
                    error = Throwable()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent and a LatencyLog record, and end the previous one") {
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchByMonitorId(monitor.id).single()

                    verifyOrder {
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency)
                        uptimeEventRepositorySpy.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt, any())
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
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
                    previousEvent = null,
                    latency = 1000
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = uptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = MonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Throwable(),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent record and end the previous one") {
                    val uptimeRecords = uptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchByMonitorId(monitor.id).single()

                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency)
                        uptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        uptimeEventRepositorySpy.endEventById(firstUptimeRecord.id, secondEvent.dispatchedAt, any())
                        uptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.UP
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].error shouldBe "Reason: 500 Internal Server Error"
                    latencyRecord.latency shouldBe firstEvent.latency
                }
            }

            `when`("it receives a MonitorDownEvent - error message needs to be redacted") {
                val monitor = createMonitor(monitorRepository)
                val event = MonitorDownEvent(
                    monitor = monitor,
                    status = null,
                    previousEvent = null,
                    error = Throwable("error".repeat(200))
                )
                eventDispatcher.dispatch(event)

                then("it should limit the error message to 255 characters and indicate that it was redacted") {
                    val expectedUptimeRecord = uptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

                    expectedUptimeRecord.error shouldHaveLength 255 + 8 + 15 // Prefix + 255 + suffix
                    expectedUptimeRecord.error shouldStartWith "Reason: "
                    expectedUptimeRecord.error shouldEndWith " ... [REDACTED]"
                }
            }
        }

        given("the DatabaseEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createMonitor(monitorRepository)
                val event = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status VALID") {
                    val expectedSSLRecord = sslEventRepository.fetchByMonitorId(event.monitor.id).single()

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
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status INVALID") {
                    val expectedSSLRecord = sslEventRepository.fetchByMonitorId(event.monitor.id).single()

                    verify(exactly = 1) { sslEventRepositorySpy.insertFromMonitorEvent(event, any()) }
                    verify(exactly = 0) { sslEventRepositorySpy.endEventById(any(), any(), any()) }

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
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new SSLEvent record") {
                    val expectedSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

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
                    previousEvent = null,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record, and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt, any())
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
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
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLInvalidEvent(
                    monitor = monitor,
                    previousEvent = firstSSLRecord,
                    error = SSLValidationError("ssl error")
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt, any())
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
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
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new SSLEvent record with status WILL_EXPIRE") {
                    val expectedSSLRecord = sslEventRepository.fetchByMonitorId(event.monitor.id).single()

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
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new SSLEvent record") {
                    val expectedSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

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
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = firstSSLRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new SSLEvent record, and end the previous one") {
                    val sslRecords = sslEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        sslEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        sslEventRepositorySpy.endEventById(firstSSLRecord.id, secondEvent.dispatchedAt, any())
                        sslEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
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

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
