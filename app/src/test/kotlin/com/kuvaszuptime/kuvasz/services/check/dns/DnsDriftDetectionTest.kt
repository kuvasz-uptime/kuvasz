package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.tables.DnsResolutionSnapshot.DNS_RESOLUTION_SNAPSHOT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.jooq.JSONB

@MicronautTest(startApplication = false)
class DnsDriftDetectionTest(
    private val uptimeChecker: DnsUptimeChecker,
    private val monitorRepository: DnsMonitorRepository,
    private val snapshotRepository: DnsResolutionSnapshotRepository,
    private val eventDispatcher: EventDispatcher,
    private val resolveExecutor: DnsResolveExecutor,
) : DatabaseBehaviorSpec() {

    @MockBean(DnsResolveExecutor::class)
    fun resolveExecutorMock(): DnsResolveExecutor = mockk()

    private fun result(
        records: Map<DnsRecordType, List<String>>,
        responseCode: DnsResponseCode = DnsResponseCode.NOERROR,
        driftRecordsComplete: Boolean = true,
    ) = DnsCheckResult(
        records = records,
        responseCode = responseCode,
        latencyMs = 10,
        error = null,
        driftRecordsComplete = driftRecordsComplete,
    )

    private fun DnsResolveExecutor.stubReturning(monitor: DnsMonitorRecord, vararg results: DnsCheckResult) {
        every {
            execute(
                host = monitor.host,
                recordTypes = any(),
                resolverHost = monitor.resolverHost,
                resolverPort = monitor.resolverPort,
                transport = monitor.transport,
                timeoutMs = monitor.timeoutMs,
                driftRecordTypes = any(),
            )
        } returnsMany results.toList()
    }

    init {
        given("DnsUptimeChecker drift detection") {

            `when`("drift detection is enabled and the monitor is checked for the first time") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
                getMock(resolveExecutor).stubReturning(monitor, result(mapOf(DnsRecordType.A to listOf("1.2.3.4"))))

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)

                then("it stores the snapshot silently without emitting a drift event") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull()
                        .shouldContainExactly(mapOf(DnsRecordType.A to listOf("1.2.3.4")))
                    subscriber.values().size shouldBe 0
                }
            }

            `when`("the resolved answer set is unchanged between two checks") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
                val records = mapOf(DnsRecordType.A to listOf("1.2.3.4"))
                getMock(resolveExecutor).stubReturning(monitor, result(records), result(records))

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("no drift event is emitted and the snapshot is unchanged") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull().shouldContainExactly(records)
                    subscriber.values().size shouldBe 0
                }
            }

            `when`("the resolved answer set changes between two checks") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"))),
                    result(mapOf(DnsRecordType.A to listOf("5.6.7.8"))),
                )

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("exactly one drift event carrying the previous and current answer sets is emitted") {
                    subscriber.awaitCount(1)
                    subscriber.values().size shouldBe 1
                    val event = subscriber.values().first()
                    event.monitor.id shouldBe monitor.id
                    event.previousRecords.shouldContainExactly(mapOf(DnsRecordType.A to listOf("1.2.3.4")))
                    event.currentRecords.shouldContainExactly(mapOf(DnsRecordType.A to listOf("5.6.7.8")))
                }

                then("the snapshot is advanced to the new answer set") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull()
                        .shouldContainExactly(mapOf(DnsRecordType.A to listOf("5.6.7.8")))
                }
            }

            `when`("drift detection is disabled") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = false)
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"))),
                    result(mapOf(DnsRecordType.A to listOf("5.6.7.8"))),
                )

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("no drift event is emitted and no snapshot is stored") {
                    snapshotRepository.getRecords(monitor.id).shouldBeNull()
                    subscriber.values().size shouldBe 0
                }
            }

            `when`("no drift record types are configured") {
                val monitor = createDnsMonitor(
                    monitorRepository,
                    driftDetectionEnabled = true,
                    recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.CONTAINS, "1.2.3.4")),
                )
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"), DnsRecordType.NS to listOf("ns1.example.com"))),
                )

                uptimeChecker.check(monitor)

                then("only the types the matchers cover are watched, ignoring anything else resolved") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull()
                        .shouldContainExactly(mapOf(DnsRecordType.A to listOf("1.2.3.4")))
                }
            }

            `when`("drift record types are configured for a type the monitor does not assert on") {
                val monitor = createDnsMonitor(
                    monitorRepository,
                    driftDetectionEnabled = true,
                    driftRecordTypes = listOf(DnsRecordType.NS),
                    recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.CONTAINS, "1.2.3.4")),
                )
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"), DnsRecordType.NS to listOf("ns1.example.com"))),
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"), DnsRecordType.NS to listOf("ns2.evil.com"))),
                )

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("drift is reported for the watched type alone") {
                    subscriber.awaitCount(1)
                    subscriber.values().size shouldBe 1
                    val event = subscriber.values().first()
                    event.previousRecords.shouldContainExactly(
                        mapOf(DnsRecordType.NS to listOf("ns1.example.com"))
                    )
                    event.currentRecords.shouldContainExactly(mapOf(DnsRecordType.NS to listOf("ns2.evil.com")))
                }

                then("the unwatched, asserted type is left out of the snapshot") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull()
                        .shouldContainExactly(mapOf(DnsRecordType.NS to listOf("ns2.evil.com")))
                }
            }

            `when`("a later check answers with a non-NOERROR response code") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"))),
                    result(mapOf(DnsRecordType.A to emptyList()), responseCode = DnsResponseCode.NXDOMAIN),
                )

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("its empty answer set is not mistaken for the records having been removed") {
                    subscriber.values().size shouldBe 0
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull()
                        .shouldContainExactly(mapOf(DnsRecordType.A to listOf("1.2.3.4")))
                }
            }

            `when`("a later check could not resolve every watched type") {
                val monitor = createDnsMonitor(
                    monitorRepository,
                    driftDetectionEnabled = true,
                    driftRecordTypes = listOf(DnsRecordType.A, DnsRecordType.MX),
                )
                getMock(resolveExecutor).stubReturning(
                    monitor,
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4"), DnsRecordType.MX to listOf("10 mail.com"))),
                    result(mapOf(DnsRecordType.A to listOf("1.2.3.4")), driftRecordsComplete = false),
                )

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the partial answer set is skipped instead of being reported as a removal") {
                    subscriber.values().size shouldBe 0
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull().shouldContainExactly(
                        mapOf(DnsRecordType.A to listOf("1.2.3.4"), DnsRecordType.MX to listOf("10 mail.com"))
                    )
                }
            }

            `when`("the stored snapshot is not readable anymore") {
                val monitor = createDnsMonitor(monitorRepository, driftDetectionEnabled = true)
                val records = mapOf(DnsRecordType.A to listOf("1.2.3.4"))
                getMock(resolveExecutor).stubReturning(monitor, result(records))
                snapshotRepository.upsert(monitor.id, records)
                // A shape this version cannot parse, as a payload written by a newer one would be after a downgrade
                dslContext
                    .update(DNS_RESOLUTION_SNAPSHOT)
                    .set(DNS_RESOLUTION_SNAPSHOT.RECORDS, JSONB.valueOf("""{"A":{"value":"1.2.3.4","ttl":60}}"""))
                    .where(DNS_RESOLUTION_SNAPSHOT.MONITOR_ID.eq(monitor.id))
                    .execute()

                val subscriber = TestSubscriber<DnsRecordsChangedEvent>()
                eventDispatcher.subscribeToDnsRecordsChangedEvents { it.forwardToSubscriber(subscriber) }

                uptimeChecker.check(monitor)

                then("the check re-seeds it rather than failing, and reports no phantom drift") {
                    snapshotRepository.getRecords(monitor.id).shouldNotBeNull().shouldContainExactly(records)
                    subscriber.values().size shouldBe 0
                }
            }
        }
    }
}
