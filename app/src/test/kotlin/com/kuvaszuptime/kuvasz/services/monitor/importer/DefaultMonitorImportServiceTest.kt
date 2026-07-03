package com.kuvaszuptime.kuvasz.services.monitor.importer

import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.services.monitor.MonitorImporter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.ValidationException

@MicronautTest(startApplication = false)
class DefaultMonitorImportServiceTest(
    private val monitorImporter: MonitorImporter,
    private val monitorImportService: MonitorImportService,
) : ShouldSpec({

    context("importMonitors") {

        should("delegate to the importer after validating adapters") {
            val importDto = MonitorImportDto()
            val validatedImport = ValidatedMonitorImport(
                httpMonitors = emptyList(),
                pushMonitors = emptyList(),
                icmpMonitors = emptyList(),
            )
            val expectedResult = MonitorImportResultDto(
                receivedMonitorCnt = 0,
                importedMonitorCnt = 0,
                deletedMonitorCount = 0,
            )
            val importerMock = getMock(monitorImporter)
            every { importerMock.importMonitorConfigs(validatedImport, false) } returns expectedResult

            val result = monitorImportService.importMonitors(importDto, dryRun = false)

            result shouldBe expectedResult
        }

        should("throw ValidationException when an adapter is invalid") {
            val importDto = MonitorImportDto(
                httpMonitors = listOf(
                    com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto(
                        name = "",
                        url = "https://example.com",
                        sensitiveUrl = false,
                        uptimeCheckInterval = 60,
                        enabled = true,
                        sslCheckEnabled = true,
                        latencyHistoryEnabled = true,
                        requestMethod = com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod.GET,
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

            shouldThrow<ValidationException> {
                monitorImportService.importMonitors(importDto, dryRun = false)
            }
        }
    }
}) {
    @MockBean(MonitorImporter::class)
    fun mockMonitorImporter(): MonitorImporter = mockk()
}
