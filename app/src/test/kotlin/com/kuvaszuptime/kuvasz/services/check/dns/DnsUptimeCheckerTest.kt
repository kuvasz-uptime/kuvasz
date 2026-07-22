package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.subscribers.TestSubscriber

@MicronautTest(startApplication = false)
class DnsUptimeCheckerTest(
    private val uptimeChecker: DnsUptimeChecker,
    private val monitorRepository: DnsMonitorRepository,
    private val uptimeEventRepository: DnsUptimeEventRepository,
    private val metricsLogRepository: DnsMetricsLogRepository,
    private val eventDispatcher: EventDispatcher,
    private val resolveExecutor: DnsResolveExecutor,
    private val checkScheduler: DnsCheckScheduler,
) : DatabaseBehaviorSpec() {

    @MockBean(DnsResolveExecutor::class)
    fun resolveExecutorMock(): DnsResolveExecutor = mockk()

    private fun DnsResolveExecutor.stub(
        monitor: DnsMonitorRecord,
        result: DnsCheckResult,
    ) {
        every {
            execute(
                host = monitor.host,
                recordTypes = any(),
                resolverHost = monitor.resolverHost,
                resolverPort = monitor.resolverPort,
                transport = monitor.transport,
                timeoutMs = monitor.timeoutMs,
            )
        } returns result
    }

    init {
        afterContainer { checkScheduler.removeAllChecks() }

        given("DnsUptimeChecker") {

            `when`("the name resolves successfully without matchers") {
                val monitor = createDnsMonitor(monitorRepository)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = mapOf(DnsRecordType.A to listOf("1.2.3.4")),
                        responseCode = DnsResponseCode.NOERROR,
                        latencyMs = 12,
                        error = null,
                    ),
                )

                val upSubscriber = TestSubscriber<DnsMonitorUpEvent>()
                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched with the latency") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                    val upEvent = upSubscriber.values().first()
                    upEvent.monitor.id shouldBe monitor.id
                    upEvent.uptimeStatus shouldBe UptimeStatus.UP
                    upEvent.latencyInMs shouldBe 12
                }

                then("no DOWN event should be dispatched") {
                    downSubscriber.values().shouldHaveSize(0)
                }

                then("the event should be persisted") {
                    val events = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    events.shouldHaveSize(1)
                    events.first().status shouldBe UptimeStatus.UP
                }
            }

            `when`("resolution fails with an error") {
                val monitor = createDnsMonitor(monitorRepository)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(records = emptyMap(), responseCode = null, latencyMs = null, error = "timed out"),
                )

                val upSubscriber = TestSubscriber<DnsMonitorUpEvent>()
                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched carrying the resolution error") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.error shouldBe "timed out"
                    downEvent.latencyInMs shouldBe null
                }

                then("no UP event should be dispatched") {
                    upSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the response code does not match the expected one") {
                val monitor = createDnsMonitor(monitorRepository, expectedResponseCode = DnsResponseCode.NOERROR)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = emptyMap(),
                        responseCode = DnsResponseCode.NXDOMAIN,
                        latencyMs = 8,
                        error = null,
                    ),
                )

                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched naming the unexpected response code") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.error shouldContain "NXDOMAIN"
                }
            }

            `when`("the expected response code is NXDOMAIN and the name does not exist") {
                val monitor = createDnsMonitor(monitorRepository, expectedResponseCode = DnsResponseCode.NXDOMAIN)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = emptyMap(),
                        responseCode = DnsResponseCode.NXDOMAIN,
                        latencyMs = 8,
                        error = null,
                    ),
                )

                val upSubscriber = TestSubscriber<DnsMonitorUpEvent>()
                eventDispatcher.subscribeToDnsMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                }
            }

            `when`("a record matcher is satisfied by the answer") {
                val monitor = createDnsMonitor(
                    monitorRepository,
                    recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")),
                )
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = mapOf(DnsRecordType.A to listOf("5.6.7.8", "1.2.3.4")),
                        responseCode = DnsResponseCode.NOERROR,
                        latencyMs = 10,
                        error = null,
                    ),
                )

                val upSubscriber = TestSubscriber<DnsMonitorUpEvent>()
                eventDispatcher.subscribeToDnsMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                }
            }

            `when`("a record matcher is not satisfied by the answer") {
                val monitor = createDnsMonitor(
                    monitorRepository,
                    recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")),
                )
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = mapOf(DnsRecordType.A to listOf("5.6.7.8")),
                        responseCode = DnsResponseCode.NOERROR,
                        latencyMs = 10,
                        error = null,
                    ),
                )

                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event naming the failing matcher should be dispatched") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.error shouldContain "1.2.3.4"
                }
            }

            `when`("resolution succeeds but latency exceeds the threshold") {
                val monitor = createDnsMonitor(monitorRepository, latencyThresholdMs = 50)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = mapOf(DnsRecordType.A to listOf("1.2.3.4")),
                        responseCode = DnsResponseCode.NOERROR,
                        latencyMs = 120,
                        error = null,
                    ),
                )

                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event carrying the measured latency should be dispatched") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.latencyInMs shouldBe 120
                }
            }

            `when`("a DOWN check occurs but failureCountThreshold is 2 (first occurrence)") {
                val monitor = createDnsMonitor(monitorRepository, failureCountThreshold = 2L)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(records = emptyMap(), responseCode = null, latencyMs = null, error = "timed out"),
                )

                val downSubscriber = TestSubscriber<DnsMonitorDownEvent>()
                eventDispatcher.subscribeToDnsMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("no DOWN event should be dispatched on first occurrence") {
                    downSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the monitor has metricsHistoryEnabled=false and a check runs") {
                val monitor = createDnsMonitor(monitorRepository, metricsHistoryEnabled = false)
                getMock(resolveExecutor).stub(
                    monitor,
                    DnsCheckResult(
                        records = mapOf(DnsRecordType.A to listOf("1.2.3.4")),
                        responseCode = DnsResponseCode.NOERROR,
                        latencyMs = 10,
                        error = null,
                    ),
                )

                uptimeChecker.check(monitor)

                then("no metrics log should be inserted") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }
        }
    }
}
