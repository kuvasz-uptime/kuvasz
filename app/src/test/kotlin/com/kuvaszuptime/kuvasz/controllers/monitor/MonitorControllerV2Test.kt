package com.kuvaszuptime.kuvasz.controllers.monitor

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.http.expectedHeadersAsMap
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest(environments = ["full-integrations-setup"])
class MonitorControllerV2Test(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("MonitorController's getMonitorsExport() endpoint") {
            val mapper = YAMLMapper()
                .registerModules(kotlinModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

            `when`("there are monitors in the database") {
                val monitor = createMonitor(
                    monitorRepository,
                    monitorName = "irrelevant",
                )
                val monitor2 = createMonitor(
                    monitorRepository,
                    enabled = false,
                    uptimeCheckInterval = 23234,
                    monitorName = "irrelevant2",
                    sslExpiryThreshold = 15,
                    expectedStatusCodes = setOf(200, 404),
                    responseTimeThresholdMillis = 1400,
                    expectedKeyword = "somethingExpected",
                    expectedKeywordCaseSensitive = true,
                    expectedKeywordNegated = false,
                    requestHeaders = mapOf(
                        "X-Test-Header" to "TestValue",
                        "1-Starts-With-Number" to "Value1",
                        "Header-With-Ending-Dash-" to "Value2",
                        "X_Underscore_Header" to "ValueWithUnderscores",
                        "-Starts-With-dash" to "DashValue",
                        "!Special#$%&'*+." to "SymbolsValue",
                        "!#$%&'*+." to "SymbolsOnlyValue",
                        "\$NotAVariable" to "ValueWithEscapedMoney",
                        "MY-FANCY-`HEADER`" to "ValueWithBackticks"
                    ),
                    expectedHeaders = mapOf(
                        "X-Expected-Header" to "ExpectedValue",
                        "1-Starts-With-Number" to "Value1",
                        "Header-With-Ending-Dash-" to "Value2",
                        "X_Underscore_Header" to "ValueWithUnderscores",
                        "-Starts-With-dash" to "DashValue",
                        "!Special#$%&'*+." to "SymbolsValue",
                        "!#$%&'*+." to "SymbolsOnlyValue",
                        "\$NotAVariable" to "ValueWithEscapedMoney",
                        "MY-FANCY-`HEADER`" to "ValueWithBackticks"
                    ),
                    requestBody = "{\"key\": \"value\"}",
                )
                val request = HttpRequest.GET<Any>("/api/v2/monitors/export/yaml").accept(MediaType.APPLICATION_YAML)

                then("it should export them in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBody(ByteArray::class.java).get()

                    response.status shouldBe HttpStatus.OK
                    with(response.headers[HttpHeaders.CONTENT_DISPOSITION]) {
                        this shouldContain "attachment;"
                        this shouldContain Regex("filename=\"kuvasz-monitors-export-\\d+\\.yml\"")
                    }
                    response.headers[HttpHeaders.CONTENT_TYPE] shouldBe MediaType.APPLICATION_YAML

                    val exportedMonitorsRaw = mapper.readTree(responseBody)["http-monitors"].shouldNotBeNull()
                    val parsedMonitors =
                        mapper.convertValue<List<HttpMonitorExportDto>>(exportedMonitorsRaw).shouldNotBeEmpty()

                    parsedMonitors.size shouldBe 2
                    parsedMonitors.forOne { firstMonitor ->
                        firstMonitor.name shouldBe monitor.name
                        firstMonitor.url shouldBe monitor.url
                        firstMonitor.uptimeCheckInterval shouldBe monitor.uptimeCheckInterval
                        firstMonitor.enabled shouldBe monitor.enabled
                        firstMonitor.sslCheckEnabled shouldBe monitor.sslCheckEnabled
                        firstMonitor.requestMethod shouldBe monitor.requestMethod
                        firstMonitor.latencyHistoryEnabled shouldBe monitor.latencyHistoryEnabled
                        firstMonitor.forceNoCache shouldBe monitor.forceNoCache
                        firstMonitor.followRedirects shouldBe monitor.followRedirects
                        firstMonitor.sslExpiryThreshold shouldBe monitor.sslExpiryThreshold
                    }
                    parsedMonitors.forOne { secondMonitor ->
                        secondMonitor.name shouldBe monitor2.name
                        secondMonitor.url shouldBe monitor2.url
                        secondMonitor.uptimeCheckInterval shouldBe monitor2.uptimeCheckInterval
                        secondMonitor.enabled shouldBe monitor2.enabled
                        secondMonitor.sslCheckEnabled shouldBe monitor2.sslCheckEnabled
                        secondMonitor.requestMethod shouldBe monitor2.requestMethod
                        secondMonitor.latencyHistoryEnabled shouldBe monitor2.latencyHistoryEnabled
                        secondMonitor.forceNoCache shouldBe monitor2.forceNoCache
                        secondMonitor.followRedirects shouldBe monitor2.followRedirects
                        secondMonitor.sslExpiryThreshold shouldBe monitor2.sslExpiryThreshold
                        secondMonitor.expectedStatusCodes shouldBe monitor2.expectedStatusCodes.toSet()
                        secondMonitor.responseTimeThresholdMillis shouldBe monitor2.responseTimeThresholdMillis
                        secondMonitor.expectedKeyword shouldBe monitor2.expectedKeyword
                        secondMonitor.expectedKeywordCaseSensitive shouldBe monitor2.expectedKeywordCaseSensitive
                        secondMonitor.expectedKeywordNegated shouldBe monitor2.expectedKeywordNegated
                        secondMonitor.requestHeaders shouldBe monitor2.requestHeadersAsMap()
                        secondMonitor.expectedHeaders shouldBe monitor2.expectedHeadersAsMap()
                        secondMonitor.requestBody shouldBe monitor2.requestBody
                    }
                }
            }

            `when`("there are no monitors in the database") {

                val request = HttpRequest.GET<Any>("/api/v2/monitors/export/yaml").accept(MediaType.APPLICATION_YAML)

                then("it should export an empty monitors list in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedMonitorsRaw = mapper.readTree(responseBody)["http-monitors"].shouldNotBeNull()
                    mapper.convertValue<List<HttpMonitorExportDto>>(exportedMonitorsRaw).shouldBeEmpty()
                }
            }
        }
    }
}
