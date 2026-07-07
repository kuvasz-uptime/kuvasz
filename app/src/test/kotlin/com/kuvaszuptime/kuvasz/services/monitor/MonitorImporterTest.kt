package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

                then("it should return the result without persisting changes") {
                    result.monitorType shouldBe MonitorType.HTTP_SSL
                    result.receivedMonitorCnt shouldBe 1
                    result.importedMonitorCnt shouldBe 1
                    result.deletedMonitorCount shouldBe 1
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
                    result.receivedMonitorCnt shouldBe 1
                    httpMonitorRepository.findByName("persisted-http").shouldNotBeNull()
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
                    result.receivedMonitorCnt shouldBe 1
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

            `when`("a dry-run import is performed") {
                val scheduledBefore = httpCheckScheduler.getScheduledUptimeChecks().keys.toSet()

                monitorImporter.batchImportMonitors(
                    httpMonitorConfigs = listOf(httpAdapter("dry-run-scheduling")),
                    pushMonitorConfigs = emptyList(),
                    icmpMonitorConfigs = emptyList(),
                    dryRun = true,
                )

                then("nothing is persisted and no checks are scheduled") {
                    httpMonitorRepository.findByName("dry-run-scheduling").shouldBeNull()
                    httpCheckScheduler.getScheduledUptimeChecks().keys.toSet() shouldBe scheduledBefore
                }
            }
        }
    }

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
