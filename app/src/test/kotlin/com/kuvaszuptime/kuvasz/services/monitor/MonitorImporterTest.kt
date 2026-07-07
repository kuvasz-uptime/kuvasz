package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class MonitorImporterTest(
    private val monitorImporter: MonitorImporter,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
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
    }
}
