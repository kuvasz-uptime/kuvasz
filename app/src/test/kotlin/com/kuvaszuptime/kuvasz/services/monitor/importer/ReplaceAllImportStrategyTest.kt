package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.PushMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class ReplaceAllImportStrategyTest(
    private val strategy: ReplaceAllImportStrategy,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the replace-all import strategy") {

            `when`("it receives monitors that do not exist yet") {
                val importDto = MonitorImportDto(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "new-http",
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
                )
                val validatedImport = ValidatedMonitorImport(
                    httpMonitors = importDto.httpMonitors!!.map { HttpMonitorImportAdapter(it) },
                    pushMonitors = emptyList(),
                    icmpMonitors = emptyList(),
                )

                val result = strategy.execute(validatedImport, dslContext)

                then("it should create the monitor") {
                    result shouldBe listOf(
                        MonitorTypeImportResult(
                            monitorType = MonitorType.HTTP_SSL,
                            receivedMonitorCnt = 1,
                            importedMonitorCnt = 1,
                            deletedMonitorCount = 0,
                        )
                    )
                    httpMonitorRepository.findByName("new-http").shouldNotBeNull()
                }
            }

            `when`("it receives monitors and the DB contains others") {
                val existingMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "to-delete")

                val importDto = MonitorImportDto(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "to-keep",
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
                )
                val validatedImport = ValidatedMonitorImport(
                    httpMonitors = importDto.httpMonitors!!.map { HttpMonitorImportAdapter(it) },
                    pushMonitors = emptyList(),
                    icmpMonitors = emptyList(),
                )

                val result = strategy.execute(validatedImport, dslContext)

                then("it should upsert the received monitor and delete the missing one") {
                    result.first().receivedMonitorCnt shouldBe 1
                    result.first().importedMonitorCnt shouldBe 1
                    result.first().deletedMonitorCount shouldBe 1
                    httpMonitorRepository.findByName("to-keep").shouldNotBeNull()
                    httpMonitorRepository.findById(existingMonitor.id, null).shouldBeNull()
                }
            }

            `when`("it receives push monitors") {
                val importDto = MonitorImportDto(
                    pushMonitors = listOf(
                        PushMonitorExportDto(
                            name = "new-push",
                            heartbeatInterval = 60,
                            gracePeriod = 30,
                            clientSecret = "ab".repeat(18),
                            enabled = true,
                            integrations = emptySet(),
                            failureCountThreshold = 1,
                        )
                    )
                )
                val validatedImport = ValidatedMonitorImport(
                    httpMonitors = emptyList(),
                    pushMonitors = importDto.pushMonitors!!.map { PushMonitorImportAdapter(it) },
                    icmpMonitors = emptyList(),
                )

                val result = strategy.execute(validatedImport, dslContext)

                then("it should create the push monitor and skip empty HTTP/ICMP types") {
                    result shouldBe listOf(
                        MonitorTypeImportResult(
                            monitorType = MonitorType.PUSH,
                            receivedMonitorCnt = 1,
                            importedMonitorCnt = 1,
                            deletedMonitorCount = 0,
                        )
                    )
                    pushMonitorRepository.findByName("new-push").shouldNotBeNull()
                }
            }

            `when`("it receives ICMP monitors") {
                val importDto = MonitorImportDto(
                    icmpMonitors = listOf(
                        IcmpMonitorExportDto(
                            name = "new-icmp",
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
                )
                val validatedImport = ValidatedMonitorImport(
                    httpMonitors = emptyList(),
                    pushMonitors = emptyList(),
                    icmpMonitors = importDto.icmpMonitors!!.map { IcmpMonitorImportAdapter(it) },
                )

                val result = strategy.execute(validatedImport, dslContext)

                then("it should create the ICMP monitor and skip empty HTTP/push types") {
                    result shouldBe listOf(
                        MonitorTypeImportResult(
                            monitorType = MonitorType.ICMP,
                            receivedMonitorCnt = 1,
                            importedMonitorCnt = 1,
                            deletedMonitorCount = 0,
                        )
                    )
                    icmpMonitorRepository.findByName("new-icmp").shouldNotBeNull()
                }
            }
        }
    }
}
