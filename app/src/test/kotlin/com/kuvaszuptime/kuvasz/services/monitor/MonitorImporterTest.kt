package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
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
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpCheckScheduler
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

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
    private val integrationIdValidator: IntegrationIdValidator,
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

            `when`("a schedulable monitor type has no registered check scheduler") {
                // Simulating a wiring error, where a schedulable type is left without its scheduler bean
                val importerWithoutSchedulers = MonitorImporter(
                    integrationIdValidator = integrationIdValidator,
                    httpMonitorRepository = httpMonitorRepository,
                    pushMonitorRepository = pushMonitorRepository,
                    icmpMonitorRepository = icmpMonitorRepository,
                    tcpMonitorRepository = tcpMonitorRepository,
                    dnsMonitorRepository = dnsMonitorRepository,
                    dslContext = dslContext,
                    checkSchedulers = emptyList(),
                )

                val thrown = shouldThrow<NoSuchElementException> {
                    importerWithoutSchedulers.batchImportMonitors(
                        httpMonitorConfigs = emptyList(),
                        pushMonitorConfigs = emptyList(),
                        icmpMonitorConfigs = emptyList(),
                        tcpMonitorConfigs = listOf(tcpAdapter("unscheduled-tcp")),
                        dnsMonitorConfigs = emptyList(),
                        dryRun = false,
                    )
                }

                then("it should fail loudly instead of silently skipping the rescheduling") {
                    thrown.shouldNotBeNull()
                    // The import itself is already committed at this point, only the rescheduling blows up
                    val tcpId = tcpMonitorRepository.findByName("unscheduled-tcp").shouldNotBeNull().id
                    tcpCheckScheduler.getScheduledUptimeChecks() shouldNotContainKey tcpId
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
    }

    private fun dnsAdapter(
        name: String,
        recordMatchers: List<DnsRecordMatcher> = emptyList(),
        driftRecordTypes: List<DnsRecordType> = emptyList(),
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
            metricsHistoryEnabled = true,
        )
    )

    private fun httpAdapter(name: String) = HttpMonitorImportAdapter(
        HttpMonitorExportDto(
            name = name,
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

    private fun tcpAdapter(name: String) = TcpMonitorImportAdapter(
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
            metricsHistoryEnabled = true,
        )
    )

    private fun pushAdapter(name: String) = PushMonitorImportAdapter(
        PushMonitorExportDto(
            name = name,
            heartbeatInterval = 60,
            gracePeriod = 30,
            clientSecret = "secret-of-$name",
            enabled = true,
            integrations = emptySet(),
            failureCountThreshold = 1,
        )
    )

    private fun icmpAdapter(name: String) = IcmpMonitorImportAdapter(
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
            metricsHistoryEnabled = true,
        )
    )
}
