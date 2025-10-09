package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.generateCertificateInfo
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
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
import kotlinx.coroutines.delay
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class DatabaseEventHandlerTest(
    httpUptimeEventRepository: HttpUptimeEventRepository,
    pushUptimeEventRepository: PushUptimeEventRepository,
    latencyLogRepository: HttpLatencyLogRepository,
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    sslEventRepository: SSLEventRepository,
    dslContext: DSLContext,
) : DatabaseBehaviorSpec() {
    init {
        val eventDispatcher = EventDispatcher()
        val httpUptimeEventRepositorySpy = spyk(httpUptimeEventRepository)
        val pushUptimeEventRepositorySpy = spyk(pushUptimeEventRepository)
        val latencyLogRepositorySpy = spyk(latencyLogRepository)
        val sslEventRepositorySpy = spyk(sslEventRepository)
        DatabaseEventHandler(
            eventDispatcher,
            httpUptimeEventRepositorySpy,
            pushUptimeEventRepositorySpy,
            latencyLogRepositorySpy,
            sslEventRepositorySpy,
            dslContext,
        )

        given("the DatabaseEventHandler - HTTP UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status UP and a LatencyLog record") {
                    val expectedUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()
                    val expectedLatencyRecord = latencyLogRepository.fetchLatestByMonitorId(event.monitor.id).single()

                    verify(exactly = 1) { httpUptimeEventRepositorySpy.insertFromMonitorEvent(event, null) }
                    verify(exactly = 0) { httpUptimeEventRepositorySpy.endEventById(any(), any(), any()) }
                    verify(exactly = 1) {
                        latencyLogRepositorySpy.insertLatencyForMonitor(
                            event.monitor.id,
                            event.latency,
                            any(),
                        )
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
                    expectedLatencyRecord.latencyInMs shouldBe event.latency
                }
            }

            `when`("it receives a MonitorUpEvent and latency history is disabled") {

                val monitor = createHttpMonitor(httpMonitorRepository, latencyHistoryEnabled = false)
                val event = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should NOT save the latency log record") {

                    httpUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()

                    verify(exactly = 1) { httpUptimeEventRepositorySpy.insertFromMonitorEvent(event, null) }
                    verify(exactly = 0) { latencyLogRepositorySpy.insertLatencyForMonitor(any(), any(), any()) }
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status DOWN") {
                    val expectedUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

                    verify(exactly = 1) { httpUptimeEventRepositorySpy.insertFromMonitorEvent(event, null) }
                    verify(exactly = 0) { httpUptimeEventRepositorySpy.endEventById(any(), any(), any()) }

                    expectedUptimeRecord.status shouldBe UptimeStatus.DOWN
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1200,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new UptimeEvent record but should create a LatencyLog record") {
                    val expectedUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                    val latencyRecords =
                        latencyLogRepository.fetchLatestByMonitorId(monitor.id).sortedBy { it.createdAt }

                    verify(exactly = 1) { httpUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any()) }
                    verify(exactly = 0) {
                        httpUptimeEventRepositorySpy.endEventById(any(), any(), any())
                    }
                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency, any())
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency, any())
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe secondEvent.dispatchedAt
                    latencyRecords shouldHaveSize 2
                    latencyRecords[0].latencyInMs shouldBe firstEvent.latency
                    latencyRecords[1].latencyInMs shouldBe secondEvent.latency
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    previousEvent = null,
                    error = Exception()
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent and a LatencyLog record, and end the previous one") {
                    val uptimeRecords = httpUptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchLatestByMonitorId(monitor.id).single()

                    verifyOrder {
                        httpUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, secondEvent.latency, any())
                        httpUptimeEventRepositorySpy.endEventById(
                            eventId = firstUptimeRecord.id,
                            endedAt = secondEvent.dispatchedAt,
                            ctx = any()
                        )
                        httpUptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.UP
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    latencyRecord.latencyInMs shouldBe secondEvent.latency
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = HttpMonitorUpEvent(
                    monitor = monitor,
                    status = HttpStatus.OK,
                    previousEvent = null,
                    latency = 1000
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    error = Exception(),
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent record and end the previous one") {
                    val uptimeRecords = httpUptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }
                    val latencyRecord = latencyLogRepository.fetchLatestByMonitorId(monitor.id).single()

                    verifyOrder {
                        latencyLogRepositorySpy.insertLatencyForMonitor(monitor.id, firstEvent.latency, any())
                        httpUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        httpUptimeEventRepositorySpy.endEventById(
                            eventId = firstUptimeRecord.id,
                            endedAt = secondEvent.dispatchedAt,
                            ctx = any()
                        )
                        httpUptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.UP
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].error shouldBe "Reason: 500 Internal Server Error"
                    latencyRecord.latencyInMs shouldBe firstEvent.latency
                }
            }

            `when`("it receives a MonitorDownEvent - error message needs to be redacted") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = HttpMonitorDownEvent(
                    monitor = monitor,
                    status = null,
                    previousEvent = null,
                    error = Exception("error".repeat(200))
                )
                eventDispatcher.dispatch(event)

                then("it should limit the error message to 255 characters and indicate that it was redacted") {
                    val expectedUptimeRecord = httpUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

                    expectedUptimeRecord.error shouldHaveLength 255 + 8 + 14 // Prefix + 255 + suffix
                    expectedUptimeRecord.error shouldStartWith "Reason: "
                    expectedUptimeRecord.error shouldEndWith "... [REDACTED]"
                }
            }
        }

        given("the DatabaseEventHandler - PUSH UPTIME events") {
            `when`("it receives a MonitorUpEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status UP") {
                    val expectedUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

                    verify(exactly = 1) { pushUptimeEventRepositorySpy.insertFromMonitorEvent(event, null) }
                    verify(exactly = 0) { pushUptimeEventRepositorySpy.endEventById(any(), any(), any()) }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
                }
            }

            `when`("it receives a MonitorDownEvent and there is no previous event for the monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val event = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "missed heartbeat"
                )
                eventDispatcher.dispatch(event)

                then("it should insert a new UptimeEvent record with status DOWN") {
                    val expectedUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(event.monitor.id).single()

                    verify(exactly = 1) { pushUptimeEventRepositorySpy.insertFromMonitorEvent(event, null) }
                    verify(exactly = 0) { pushUptimeEventRepositorySpy.endEventById(any(), any(), any()) }

                    expectedUptimeRecord.status shouldBe UptimeStatus.DOWN
                    expectedUptimeRecord.startedAt shouldBe event.dispatchedAt
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe event.dispatchedAt
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should not insert a new UptimeEvent record") {
                    val expectedUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                    verify(exactly = 1) { pushUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any()) }
                    verify(exactly = 0) {
                        pushUptimeEventRepositorySpy.endEventById(any(), any(), any())
                    }

                    expectedUptimeRecord.status shouldBe UptimeStatus.UP
                    expectedUptimeRecord.endedAt shouldBe null
                    expectedUptimeRecord.updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives a MonitorUpEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    previousEvent = null,
                    error = "missed something"
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent and end the previous one") {
                    val uptimeRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        pushUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        pushUptimeEventRepositorySpy.endEventById(
                            eventId = firstUptimeRecord.id,
                            endedAt = secondEvent.dispatchedAt,
                            ctx = any()
                        )
                        pushUptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.UP
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with different status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorUpEvent(
                    monitor = monitor,
                    previousEvent = null,
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "missed heartbeat",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should create a new UptimeEvent record and end the previous one") {
                    val uptimeRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id).sortedBy { it.startedAt }

                    verifyOrder {
                        pushUptimeEventRepositorySpy.insertFromMonitorEvent(firstEvent, any())
                        pushUptimeEventRepositorySpy.endEventById(
                            eventId = firstUptimeRecord.id,
                            endedAt = secondEvent.dispatchedAt,
                            ctx = any()
                        )
                        pushUptimeEventRepositorySpy.insertFromMonitorEvent(secondEvent, any())
                    }

                    uptimeRecords[0].status shouldBe UptimeStatus.UP
                    uptimeRecords[0].endedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[0].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].status shouldBe UptimeStatus.DOWN
                    uptimeRecords[1].endedAt shouldBe null
                    uptimeRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    uptimeRecords[1].error shouldBe "Reason: missed heartbeat"
                }
            }

            `when`("it receives a MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "first error",
                    previousEvent = null,
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                delay(1000)

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "missed heartbeat",
                    previousEvent = firstUptimeRecord
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should update the updatedAt timestamp on the previous event") {
                    val uptimeRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id)

                    verifyOrder {
                        pushUptimeEventRepositorySpy.updateEvent(firstUptimeRecord.id, any())
                    }

                    uptimeRecords.shouldHaveSize(1).forOne { event ->
                        event.status shouldBe UptimeStatus.DOWN
                        event.endedAt.shouldBeNull()
                        event.updatedAt shouldBeAfter firstUptimeRecord.updatedAt
                        event.error shouldBe firstUptimeRecord.error
                    }
                }
            }

            `when`("it receives a manual MonitorDownEvent and there is a previous event with the same status") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val firstEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "first error",
                    previousEvent = null,
                )
                eventDispatcher.dispatch(firstEvent)
                val firstUptimeRecord = pushUptimeEventRepository.fetchByMonitorId(monitor.id).single()
                delay(1000)

                val secondEvent = PushMonitorDownEvent(
                    monitor = monitor,
                    error = "missed heartbeat",
                    previousEvent = firstUptimeRecord,
                    isManual = true,
                )
                eventDispatcher.dispatch(secondEvent)

                then("it should update the updatedAt timestamp and also the error on the previous event") {
                    val uptimeRecords = pushUptimeEventRepository.fetchByMonitorId(monitor.id)

                    verifyOrder {
                        pushUptimeEventRepositorySpy.updateEvent(firstUptimeRecord.id, any())
                    }

                    uptimeRecords.shouldHaveSize(1).forOne { event ->
                        event.status shouldBe UptimeStatus.DOWN
                        event.endedAt.shouldBeNull()
                        event.updatedAt shouldBeAfter firstUptimeRecord.updatedAt
                        event.error shouldBe "Reason: ${secondEvent.error}"
                    }
                }
            }
        }

        given("the DatabaseEventHandler - SSL events") {
            `when`("it receives an SSLValidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    expectedSSLRecord.sslExpiryDate shouldBe event.certInfo.validTo
                }
            }

            `when`("it receives an SSLInvalidEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    expectedSSLRecord.sslExpiryDate shouldBe null
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.plusDays(5)),
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
                    expectedSSLRecord.sslExpiryDate shouldBe secondEvent.certInfo.validTo
                }
            }

            `when`("it receives an SSLValidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    sslRecords[0].sslExpiryDate shouldBe null
                    sslRecords[1].status shouldBe SslStatus.VALID
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].sslExpiryDate shouldBe secondEvent.certInfo.validTo
                }
            }

            `when`("it receives an SSLInvalidEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    sslRecords[0].sslExpiryDate shouldBe firstEvent.certInfo.validTo
                    sslRecords[1].status shouldBe SslStatus.INVALID
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].sslExpiryDate shouldBe null
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is no previous event for the monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    expectedSSLRecord.sslExpiryDate shouldBe event.certInfo.validTo
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with the same status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
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
                    expectedSSLRecord.sslExpiryDate shouldBe secondEvent.certInfo.validTo
                }
            }

            `when`("it receives an SSLWillExpireEvent and there is a previous event with different status") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val firstEvent = SSLValidEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(),
                    previousEvent = null
                )
                eventDispatcher.dispatch(firstEvent)
                val firstSSLRecord = sslEventRepository.fetchByMonitorId(monitor.id).single()

                val secondEvent = SSLWillExpireEvent(
                    monitor = monitor,
                    certInfo = generateCertificateInfo(validTo = firstEvent.certInfo.validTo.minusDays(10)),
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
                    sslRecords[0].sslExpiryDate shouldBe firstEvent.certInfo.validTo
                    sslRecords[1].status shouldBe SslStatus.WILL_EXPIRE
                    sslRecords[1].endedAt shouldBe null
                    sslRecords[1].updatedAt shouldBe secondEvent.dispatchedAt
                    sslRecords[1].sslExpiryDate shouldBe secondEvent.certInfo.validTo
                }
            }
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
        super.afterTest(testCase, result)
    }
}
