package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.PendingFailure.PENDING_FAILURE
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPendingFailure
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.DnsMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.PushMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.TcpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorLifecycleEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpCheckScheduler
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.subscribers.TestSubscriber
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@MicronautTest
class MonitorImporterTest(
    private val monitorImporter: MonitorImporter,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val httpCheckScheduler: HttpCheckScheduler,
    private val icmpCheckScheduler: IcmpCheckScheduler,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val snapshotRepository: DnsResolutionSnapshotRepository,
    private val dnsCheckScheduler: DnsCheckScheduler,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val tcpCheckScheduler: TcpCheckScheduler,
    private val pushMonitorRepository: PushMonitorRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val icmpMetricsLogRepository: IcmpMetricsLogRepository,
    private val tcpMetricsLogRepository: TcpMetricsLogRepository,
    private val dnsMetricsLogRepository: DnsMetricsLogRepository,
    private val eventDispatcher: EventDispatcher,
    private val statusPageDataActions: StatusPageDataActions,
) : DatabaseBehaviorSpec() {
    init {

        given("MonitorImporter.importHttpMonitorConfigs()") {

            `when`("dryRun is true") {
                val existingMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "existing")

                val httpMonitor = HttpMonitorImportAdapter(
                    HttpMonitorExportDto(
                        name = "dry-run-http",
                        url = "https://example.com",
                        sensitiveUrl = false,
                        uptimeCheckInterval = 60,
                        enabled = true,
                        sslCheckEnabled = true,
                        latencyHistoryEnabled = true,
                        requestMethod = HttpMethod.GET,
                        followRedirects = true,
                        forceNoCache = true,
                        sslExpiryThreshold = 30,
                        failureCountThreshold = 1,
                        integrations = emptySet(),
                        expectedStatusCodes = emptySet(),
                        responseTimeThresholdMillis = null,
                        expectedKeyword = null,
                        expectedKeywordCaseSensitive = false,
                        expectedKeywordNegated = false,
                        requestHeaders = emptyMap(),
                        expectedHeaders = emptyMap(),
                        requestBody = null,
                    )
                )

                val result = monitorImporter.importHttpMonitorConfigs(listOf(httpMonitor), dryRun = true)

                then("it should return the result with the affected monitors without persisting changes") {
                    result.monitorType shouldBe MonitorType.HTTP_SSL
                    result.receivedCnt shouldBe 1
                    result.imported shouldContainExactly listOf(MonitorID(MonitorType.HTTP_SSL, "dry-run-http"))
                    result.deleted shouldContainExactly listOf(MonitorID(MonitorType.HTTP_SSL, "existing"))
                    result.ignoredIntegrations.shouldBeEmpty()
                    httpMonitorRepository.findById(existingMonitor.id, null).shouldNotBeNull()
                    httpMonitorRepository.findByName("dry-run-http").shouldBeNull()
                }
            }

            `when`("dryRun is false") {
                val httpMonitor = HttpMonitorImportAdapter(
                    HttpMonitorExportDto(
                        name = "persisted-http",
                        url = "https://example.com",
                        sensitiveUrl = false,
                        uptimeCheckInterval = 60,
                        enabled = true,
                        sslCheckEnabled = true,
                        latencyHistoryEnabled = true,
                        requestMethod = HttpMethod.GET,
                        followRedirects = true,
                        forceNoCache = true,
                        sslExpiryThreshold = 30,
                        failureCountThreshold = 1,
                        integrations = emptySet(),
                        expectedStatusCodes = emptySet(),
                        responseTimeThresholdMillis = null,
                        expectedKeyword = null,
                        expectedKeywordCaseSensitive = false,
                        expectedKeywordNegated = false,
                        requestHeaders = emptyMap(),
                        expectedHeaders = emptyMap(),
                        requestBody = null,
                    )
                )

                val result = monitorImporter.importHttpMonitorConfigs(listOf(httpMonitor), dryRun = false)

                then("it should persist the imported monitor") {
                    result.monitorType shouldBe MonitorType.HTTP_SSL
                    result.receivedCnt shouldBe 1
                    httpMonitorRepository.findByName("persisted-http").shouldNotBeNull()
                }
            }

            `when`("a monitor references a non-configured integration") {
                val ghostIntegration = IntegrationID(IntegrationType.SLACK, "ghost")
                val httpMonitor = HttpMonitorImportAdapter(
                    HttpMonitorExportDto(
                        name = "with-ghost-integration",
                        url = "https://example.com",
                        sensitiveUrl = false,
                        uptimeCheckInterval = 60,
                        enabled = true,
                        sslCheckEnabled = true,
                        latencyHistoryEnabled = true,
                        requestMethod = HttpMethod.GET,
                        followRedirects = true,
                        forceNoCache = true,
                        sslExpiryThreshold = 30,
                        failureCountThreshold = 1,
                        integrations = setOf(ghostIntegration),
                        expectedStatusCodes = emptySet(),
                        responseTimeThresholdMillis = null,
                        expectedKeyword = null,
                        expectedKeywordCaseSensitive = false,
                        expectedKeywordNegated = false,
                        requestHeaders = emptyMap(),
                        expectedHeaders = emptyMap(),
                        requestBody = null,
                    )
                )

                val results = monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpMonitor),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = emptyList(),
                    tcpMonitorConfigs = emptyList(),
                    dnsMonitorConfigs = emptyList(),
                    dryRun = false,
                )

                then("the non-configured integration is dropped, reported as ignored, and the monitor is imported") {
                    val result = results.single()
                    result.receivedCnt shouldBe 1
                    result.ignoredIntegrations shouldContainExactly listOf(ghostIntegration.toString())
                    val persisted = httpMonitorRepository.findByName("with-ghost-integration").shouldNotBeNull()
                    persisted.integrations.toList().shouldBeEmpty()
                }
            }
        }

        given("MonitorImporter.importIcmpMonitorConfigs()") {

            `when`("it receives ICMP monitors and dryRun is false") {
                val icmpMonitor = IcmpMonitorImportAdapter(
                    IcmpMonitorExportDto(
                        name = "persisted-icmp",
                        host = "1.2.3.4",
                        uptimeCheckInterval = 60,
                        packetCount = 4,
                        timeoutSeconds = 5,
                        packetLossThreshold = 50,
                        failureCountThreshold = 1,
                        enabled = true,
                        integrations = emptySet(),
                        metricsHistoryEnabled = true,
                    )
                )

                val result = monitorImporter.importIcmpMonitorConfigs(listOf(icmpMonitor), dryRun = false)

                then("it should persist the ICMP monitor") {
                    result.monitorType shouldBe MonitorType.ICMP
                    result.receivedCnt shouldBe 1
                    icmpMonitorRepository.findByName("persisted-icmp").shouldNotBeNull()
                }
            }
        }

        given("MonitorImporter.batchImportMonitors()") {

            `when`("the backup contains no entry for a monitor type that already exists in the database") {
                val existingHttp = createHttpMonitor(httpMonitorRepository, monitorName = "existing-http")
                val existingIcmp = createIcmpMonitor(icmpMonitorRepository, monitorName = "existing-icmp")

                val importedHttp = HttpMonitorImportAdapter(
                    HttpMonitorExportDto(
                        name = "imported-http",
                        url = "https://example.com",
                        sensitiveUrl = false,
                        uptimeCheckInterval = 60,
                        enabled = true,
                        sslCheckEnabled = true,
                        latencyHistoryEnabled = true,
                        requestMethod = HttpMethod.GET,
                        followRedirects = true,
                        forceNoCache = true,
                        sslExpiryThreshold = 30,
                        failureCountThreshold = 1,
                        integrations = emptySet(),
                        expectedStatusCodes = emptySet(),
                        responseTimeThresholdMillis = null,
                        expectedKeyword = null,
                        expectedKeywordCaseSensitive = false,
                        expectedKeywordNegated = false,
                        requestHeaders = emptyMap(),
                        expectedHeaders = emptyMap(),
                        requestBody = null,
                    )
                )

                val results = monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(importedHttp),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = emptyList(),
                    tcpMonitorConfigs = emptyList(),
                    dnsMonitorConfigs = emptyList(),
                    dryRun = false,
                )

                then("it should reconcile only the present type and leave the absent types untouched") {
                    results.map { it.monitorType } shouldBe listOf(MonitorType.HTTP_SSL)

                    // The HTTP monitor that was not in the backup gets deleted, the backup one is created
                    httpMonitorRepository.findById(existingHttp.id, null).shouldBeNull()
                    httpMonitorRepository.findByName("imported-http").shouldNotBeNull()

                    // The ICMP monitor must survive, because ICMP was absent from the backup
                    icmpMonitorRepository.findById(existingIcmp.id, null).shouldNotBeNull()
                }
            }

            `when`("a real import persists HTTP and ICMP monitors") {
                val results = monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpAdapter("scheduled-http")),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = listOf(icmpAdapter("scheduled-icmp")),
                    tcpMonitorConfigs = emptyList(),
                    dnsMonitorConfigs = emptyList(),
                    dryRun = false,
                )

                then("the imported monitors are scheduled for checks right away, not only after a restart") {
                    results.map { it.monitorType } shouldContainExactlyInAnyOrder
                        listOf(MonitorType.HTTP_SSL, MonitorType.ICMP)

                    val httpId = httpMonitorRepository.findByName("scheduled-http").shouldNotBeNull().id
                    val icmpId = icmpMonitorRepository.findByName("scheduled-icmp").shouldNotBeNull().id
                    httpCheckScheduler.getScheduledUptimeChecks()[httpId].shouldNotBeNull()
                    icmpCheckScheduler.getScheduledUptimeChecks()[icmpId].shouldNotBeNull()
                }
            }

            `when`("a real import persists TCP and push monitors") {
                val results = monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = emptyList(),
                    pushMonitorConfigs = listOf(pushAdapter("scheduled-push")),
                    icmpMonitorConfigs = emptyList(),
                    tcpMonitorConfigs = listOf(tcpAdapter("scheduled-tcp")),
                    dnsMonitorConfigs = emptyList(),
                    dryRun = false,
                )

                then("only the TCP monitor gets scheduled, the push one has no scheduler at all") {
                    results.map { it.monitorType } shouldContainExactlyInAnyOrder
                        listOf(MonitorType.TCP, MonitorType.PUSH)

                    val tcpId = tcpMonitorRepository.findByName("scheduled-tcp").shouldNotBeNull().id
                    // The TCP monitor is the only scheduled check, the push import doesn't schedule anything
                    tcpCheckScheduler.getScheduledUptimeChecks() shouldHaveSize 1
                    tcpCheckScheduler.getScheduledUptimeChecks()[tcpId].shouldNotBeNull()
                    pushMonitorRepository.findByName("scheduled-push").shouldNotBeNull()
                }
            }

            `when`("a dry-run import is performed") {
                val scheduledBefore = httpCheckScheduler.getScheduledUptimeChecks().keys.toSet()

                monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpAdapter("dry-run-scheduling")),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = emptyList(),
                    tcpMonitorConfigs = emptyList(),
                    dnsMonitorConfigs = emptyList(),
                    dryRun = true,
                )

                then("nothing is persisted and no checks are scheduled") {
                    httpMonitorRepository.findByName("dry-run-scheduling").shouldBeNull()
                    httpCheckScheduler.getScheduledUptimeChecks().keys.toSet() shouldBe scheduledBefore
                }
            }
        }

        given("MonitorImporter.importDnsMonitorConfigs()") {

            `when`("a real import persists a DNS monitor with matchers and a drift watch list") {
                val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4"))
                val results = monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = emptyList(),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = emptyList(),
                    tcpMonitorConfigs = emptyList(),
                    dnsMonitorConfigs = listOf(dnsAdapter("imported-dns", matchers, listOf(DnsRecordType.NS))),
                    dryRun = false,
                )

                then("every DNS-specific field survives the round-trip and the monitor is scheduled") {
                    val result = results.single()
                    result.monitorType shouldBe MonitorType.DNS
                    result.receivedCnt shouldBe 1

                    val persisted = dnsMonitorRepository.findByName("imported-dns").shouldNotBeNull()
                    persisted.resolverHost shouldBe "1.1.1.1"
                    persisted.resolverPort shouldBe 5353
                    persisted.transport shouldBe DnsTransport.TCP
                    persisted.driftDetectionEnabled shouldBe true
                    persisted.driftRecordTypes.toList() shouldContainExactly listOf(DnsRecordType.NS)
                    persisted.recordMatchersAsList() shouldContainExactly matchers

                    dnsCheckScheduler.getScheduledUptimeChecks()[persisted.id].shouldNotBeNull()
                }
            }

            `when`("a dry-run DNS import is performed") {
                val result = monitorImporter.importDnsMonitorConfigs(listOf(dnsAdapter("dry-run-dns")), dryRun = true)

                then("the result is reported but nothing is persisted") {
                    result.receivedCnt shouldBe 1
                    dnsMonitorRepository.findByName("dry-run-dns").shouldBeNull()
                }
            }

            `when`("a DNS monitor is not part of a subsequent import") {
                monitorImporter.importDnsMonitorConfigs(listOf(dnsAdapter("dns-to-delete")), dryRun = false)

                monitorImporter.importDnsMonitorConfigs(listOf(dnsAdapter("dns-to-keep")), dryRun = false)

                then("it is reconciled away, like the other monitor types") {
                    dnsMonitorRepository.findByName("dns-to-delete").shouldBeNull()
                    dnsMonitorRepository.findByName("dns-to-keep").shouldNotBeNull()
                }
            }

            `when`("a re-import changes the drift config of an existing DNS monitor") {
                monitorImporter.importDnsMonitorConfigs(
                    listOf(dnsAdapter("drift-reload", driftRecordTypes = listOf(DnsRecordType.NS))),
                    dryRun = false,
                )
                val monitor = dnsMonitorRepository.findByName("drift-reload").shouldNotBeNull()
                snapshotRepository.upsert(monitor.id, mapOf(DnsRecordType.A to listOf("1.2.3.4")))

                monitorImporter.importDnsMonitorConfigs(
                    listOf(dnsAdapter("drift-reload", driftRecordTypes = listOf(DnsRecordType.MX))),
                    dryRun = false,
                )

                then("the upsert fires the reset trigger, so the stale snapshot is dropped") {
                    snapshotRepository.getSnapshot(monitor.id).shouldBeNull()
                }
            }

            `when`("a re-import leaves an existing DNS monitor's config unchanged") {
                monitorImporter.importDnsMonitorConfigs(
                    listOf(dnsAdapter("drift-stable", driftRecordTypes = listOf(DnsRecordType.NS))),
                    dryRun = false,
                )
                val monitor = dnsMonitorRepository.findByName("drift-stable").shouldNotBeNull()
                val records = mapOf(DnsRecordType.A to listOf("1.2.3.4"))
                snapshotRepository.upsert(monitor.id, records)

                monitorImporter.importDnsMonitorConfigs(
                    listOf(dnsAdapter("drift-stable", driftRecordTypes = listOf(DnsRecordType.NS))),
                    dryRun = false,
                )

                then("the identical upsert keeps the baseline (survives no-op restarts)") {
                    snapshotRepository.getSnapshot(monitor.id)?.records.shouldNotBeNull().shouldContainExactly(records)
                }
            }
        }

        given("the side effects of an import") {

            `when`("a real import upserts and deletes monitors of every type") {
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val toDelete = createHttpMonitor(httpMonitorRepository, monitorName = "http-to-delete")
                delay(1000.milliseconds)

                then("every affected monitor is announced and the status page caches are dropped") {
                    monitorImporter.batchImportMonitors(
                        httpMonitorConfigs = listOf(httpAdapter("http-kept")),
                        pushMonitorConfigs = listOf(pushAdapter("push-kept")),
                        icmpMonitorConfigs = listOf(icmpAdapter("icmp-kept")),
                        tcpMonitorConfigs = listOf(tcpAdapter("tcp-kept")),
                        dnsMonitorConfigs = listOf(dnsAdapter("dns-kept")),
                        dryRun = false,
                    )

                    val httpKept = httpMonitorRepository.findByName("http-kept").shouldNotBeNull()
                    val pushKept = pushMonitorRepository.findByName("push-kept").shouldNotBeNull()
                    val icmpKept = icmpMonitorRepository.findByName("icmp-kept").shouldNotBeNull()
                    val tcpKept = tcpMonitorRepository.findByName("tcp-kept").shouldNotBeNull()
                    val dnsKept = dnsMonitorRepository.findByName("dns-kept").shouldNotBeNull()

                    subscriber.awaitCount(6).values() shouldContainExactlyInAnyOrder listOf(
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.HTTP_SSL, httpKept.id)),
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.PUSH, pushKept.id)),
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.ICMP, icmpKept.id)),
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.TCP, tcpKept.id)),
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.DNS, dnsKept.id)),
                        MonitorDeleteEvent(NumericMonitorID(MonitorType.HTTP_SSL, toDelete.id)),
                    )
                    verify(exactly = 1) { getMock(statusPageDataActions).invalidateAllCaches() }
                }
            }

            `when`("a single type is imported through its own entry point") {
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                delay(1000.milliseconds)

                then("the change is announced the same way as from a batch import") {
                    monitorImporter.importIcmpMonitorConfigs(listOf(icmpAdapter("icmp-announced")), dryRun = false)

                    val imported = icmpMonitorRepository.findByName("icmp-announced").shouldNotBeNull()

                    subscriber.awaitCount(1).values() shouldContainExactlyInAnyOrder listOf(
                        MonitorUpdateEvent(NumericMonitorID(MonitorType.ICMP, imported.id)),
                    )
                    verify(exactly = 1) { getMock(statusPageDataActions).invalidateAllCaches() }
                }
            }

            `when`("a dry-run import would change everything") {
                val subscriber = TestSubscriber<MonitorLifecycleEvent>()
                eventDispatcher.subscribeToMonitorLifecycleEvents { it.forwardToSubscriber(subscriber) }
                val http = createHttpMonitor(httpMonitorRepository, monitorName = "dry-run-untouched")
                val push = createPushMonitor(pushMonitorRepository, monitorName = "dry-run-push")
                latencyLogRepository.insertLatencyForMonitor(http.id, 100)
                createPendingFailure(dslContext, push.id)
                delay(1000.milliseconds)

                then("nothing is announced, no cache is dropped and the derived data is rolled back with the rest") {
                    monitorImporter.batchImportMonitors(
                        httpMonitorConfigs = listOf(httpAdapter("dry-run-untouched", latencyHistoryEnabled = false)),
                        pushMonitorConfigs = listOf(pushAdapter("dry-run-push", heartbeatInterval = 900)),
                        icmpMonitorConfigs = emptyList(),
                        tcpMonitorConfigs = emptyList(),
                        dnsMonitorConfigs = emptyList(),
                        dryRun = true,
                    )

                    subscriber.values().shouldBeEmpty()
                    verify(exactly = 0) { getMock(statusPageDataActions).invalidateAllCaches() }
                    latencyLogRepository.fetchLastByMonitorId(http.id).shouldNotBeNull()
                    pendingFailureCountOf(push.id) shouldBe 1L
                }
            }

            `when`("an import turns the latency and the metrics history off") {
                val http = createHttpMonitor(httpMonitorRepository, monitorName = "http-history")
                val icmp = createIcmpMonitor(icmpMonitorRepository, monitorName = "icmp-history")
                val tcp = createTcpMonitor(tcpMonitorRepository, monitorName = "tcp-history")
                val dns = createDnsMonitor(dnsMonitorRepository, monitorName = "dns-history")
                latencyLogRepository.insertLatencyForMonitor(http.id, 100)
                icmpMetricsLogRepository.insertLog(icmp.id, latencyMs = 100, packetLossPercentage = 0)
                tcpMetricsLogRepository.insertLog(tcp.id, latencyMs = 100)
                dnsMetricsLogRepository.insertLog(dns.id, latencyMs = 100)

                monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpAdapter("http-history", latencyHistoryEnabled = false)),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = listOf(icmpAdapter("icmp-history", metricsHistoryEnabled = false)),
                    tcpMonitorConfigs = listOf(tcpAdapter("tcp-history", metricsHistoryEnabled = false)),
                    dnsMonitorConfigs = listOf(dnsAdapter("dns-history", metricsHistoryEnabled = false)),
                    dryRun = false,
                )

                then("the logs that are not reachable anymore are deleted") {
                    latencyLogRepository.fetchLastByMonitorId(http.id).shouldBeNull()
                    icmpMetricsLogRepository.fetchLastByMonitorId(icmp.id).shouldBeNull()
                    tcpMetricsLogRepository.fetchLastByMonitorId(tcp.id).shouldBeNull()
                    dnsMetricsLogRepository.fetchLastByMonitorId(dns.id).shouldBeNull()
                }
            }

            `when`("an import leaves the latency and the metrics history on") {
                val http = createHttpMonitor(httpMonitorRepository, monitorName = "http-history-kept")
                val icmp = createIcmpMonitor(icmpMonitorRepository, monitorName = "icmp-history-kept")
                val tcp = createTcpMonitor(tcpMonitorRepository, monitorName = "tcp-history-kept")
                val dns = createDnsMonitor(dnsMonitorRepository, monitorName = "dns-history-kept")
                latencyLogRepository.insertLatencyForMonitor(http.id, 100)
                icmpMetricsLogRepository.insertLog(icmp.id, latencyMs = 100, packetLossPercentage = 0)
                tcpMetricsLogRepository.insertLog(tcp.id, latencyMs = 100)
                dnsMetricsLogRepository.insertLog(dns.id, latencyMs = 100)

                monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpAdapter("http-history-kept")),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = listOf(icmpAdapter("icmp-history-kept")),
                    tcpMonitorConfigs = listOf(tcpAdapter("tcp-history-kept")),
                    dnsMonitorConfigs = listOf(dnsAdapter("dns-history-kept")),
                    dryRun = false,
                )

                then("the already recorded logs are kept") {
                    latencyLogRepository.fetchLastByMonitorId(http.id).shouldNotBeNull()
                    icmpMetricsLogRepository.fetchLastByMonitorId(icmp.id).shouldNotBeNull()
                    tcpMetricsLogRepository.fetchLastByMonitorId(tcp.id).shouldNotBeNull()
                    dnsMetricsLogRepository.fetchLastByMonitorId(dns.id).shouldNotBeNull()
                }
            }

            `when`("an import changes the failure counting settings of an existing push monitor") {
                monitorImporter.importPushMonitorConfigs(listOf(pushAdapter("push-recounted")), dryRun = false)
                val monitor = pushMonitorRepository.findByName("push-recounted").shouldNotBeNull()
                createPendingFailure(dslContext, monitor.id)

                monitorImporter.importPushMonitorConfigs(
                    listOf(pushAdapter("push-recounted", failureCountThreshold = 5)),
                    dryRun = false,
                )

                then("the recorded failures are dropped, because they are not comparable to the new settings") {
                    pendingFailureCountOf(monitor.id).shouldBeNull()
                }
            }

            `when`("an import leaves the failure counting settings of an existing push monitor intact") {
                monitorImporter.importPushMonitorConfigs(listOf(pushAdapter("push-untouched")), dryRun = false)
                val monitor = pushMonitorRepository.findByName("push-untouched").shouldNotBeNull()
                createPendingFailure(dslContext, monitor.id, failureCount = 2)

                monitorImporter.importPushMonitorConfigs(listOf(pushAdapter("push-untouched")), dryRun = false)

                then("the recorded failures survive the import") {
                    pendingFailureCountOf(monitor.id) shouldBe 2L
                }
            }
        }
    }

    private fun pendingFailureCountOf(monitorId: Long): Long? = dslContext
        .selectFrom(PENDING_FAILURE)
        .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
        .fetchOne()
        ?.failureCount

    @MockBean(StatusPageDataActions::class)
    fun statusPageDataActionsMock(): StatusPageDataActions = mockk(relaxUnitFun = true)

    private fun dnsAdapter(
        name: String,
        recordMatchers: List<DnsRecordMatcher> = emptyList(),
        driftRecordTypes: List<DnsRecordType> = emptyList(),
        metricsHistoryEnabled: Boolean = true,
    ) = DnsMonitorImportAdapter(
        DnsMonitorExportDto(
            name = name,
            host = "example.com",
            resolverHost = "1.1.1.1",
            resolverPort = 5353,
            transport = DnsTransport.TCP,
            recordMatchers = recordMatchers,
            expectedResponseCode = DnsResponseCode.NOERROR,
            driftDetectionEnabled = true,
            driftRecordTypes = driftRecordTypes,
            uptimeCheckInterval = 60,
            timeoutMs = 5000,
            latencyThresholdMs = 250,
            failureCountThreshold = 2,
            enabled = true,
            integrations = emptySet(),
            metricsHistoryEnabled = metricsHistoryEnabled,
        )
    )

    private fun httpAdapter(name: String, latencyHistoryEnabled: Boolean = true) = HttpMonitorImportAdapter(
        HttpMonitorExportDto(
            name = name,
            url = "https://example.com",
            sensitiveUrl = false,
            uptimeCheckInterval = 60,
            enabled = true,
            sslCheckEnabled = true,
            latencyHistoryEnabled = latencyHistoryEnabled,
            requestMethod = HttpMethod.GET,
            followRedirects = true,
            forceNoCache = true,
            sslExpiryThreshold = 30,
            failureCountThreshold = 1,
            integrations = emptySet(),
            expectedStatusCodes = emptySet(),
            responseTimeThresholdMillis = null,
            expectedKeyword = null,
            expectedKeywordCaseSensitive = false,
            expectedKeywordNegated = false,
            requestHeaders = emptyMap(),
            expectedHeaders = emptyMap(),
            requestBody = null,
        )
    )

    private fun tcpAdapter(name: String, metricsHistoryEnabled: Boolean = true) = TcpMonitorImportAdapter(
        TcpMonitorExportDto(
            name = name,
            host = "1.2.3.4",
            port = 8080,
            uptimeCheckInterval = 60,
            timeoutMs = 5000,
            latencyThresholdMs = 250,
            failureCountThreshold = 1,
            enabled = true,
            integrations = emptySet(),
            metricsHistoryEnabled = metricsHistoryEnabled,
        )
    )

    private fun pushAdapter(
        name: String,
        heartbeatInterval: Long = 60,
        gracePeriod: Long = 30,
        failureCountThreshold: Long = 1,
    ) = PushMonitorImportAdapter(
        PushMonitorExportDto(
            name = name,
            heartbeatInterval = heartbeatInterval,
            gracePeriod = gracePeriod,
            clientSecret = "secret-of-$name",
            enabled = true,
            integrations = emptySet(),
            failureCountThreshold = failureCountThreshold,
        )
    )

    private fun icmpAdapter(name: String, metricsHistoryEnabled: Boolean = true) = IcmpMonitorImportAdapter(
        IcmpMonitorExportDto(
            name = name,
            host = "1.2.3.4",
            uptimeCheckInterval = 60,
            packetCount = 4,
            timeoutSeconds = 5,
            packetLossThreshold = 50,
            failureCountThreshold = 1,
            enabled = true,
            integrations = emptySet(),
            metricsHistoryEnabled = metricsHistoryEnabled,
        )
    )
}
