package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.http.expectedHeadersAsMap
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.kotlinModule

@MicronautTest(environments = ["full-integrations-setup"])
class MonitorControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        fun buildYamlImportContent(
            httpMonitors: List<HttpMonitorExportDto> = emptyList(),
            pushMonitors: List<PushMonitorExportDto> = emptyList(),
            icmpMonitors: List<IcmpMonitorExportDto> = emptyList(),
        ): ByteArray {
            val importMapper = YAMLMapper.builder()
                .addModules(kotlinModule())
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .build()

            val content = mapOf(
                "http-monitors" to httpMonitors,
                "push-monitors" to pushMonitors,
                "icmp-monitors" to icmpMonitors,
            )

            return importMapper.writeValueAsBytes(content)
        }

        given("MonitorController's getMonitorsExport() endpoint") {
            val mapper = YAMLMapper.builder()
                .addModules(kotlinModule())
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .build()

            `when`("there are monitors in the database") {
                val httpMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "irrelevant",
                )
                val httpMonitor2 = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = false,
                    sensitiveUrl = true,
                    uptimeCheckInterval = 23234,
                    monitorName = "irrelevant2",
                    sslExpiryThreshold = 15,
                    failureCountThreshold = 5,
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
                    integrations = listOf(
                        IntegrationID(IntegrationType.SLACK, "disabled"),
                        IntegrationID(IntegrationType.DISCORD, "global"),
                    )
                )
                val pushMonitor = createPushMonitor(
                    pushMonitorRepository,
                    monitorName = "irrelevant3",
                    integrations = listOf(
                        IntegrationID(IntegrationType.EMAIL, "Global-343"),
                        IntegrationID(IntegrationType.PAGERDUTY, "global"),
                    )
                )
                val pushMonitor2 = createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    heartbeatInterval = 12345,
                    monitorName = "irrelevant4",
                    gracePeriod = 54321,
                    clientSecret = "ab".repeat(18),
                    failureCountThreshold = 3,
                )
                val icmpMonitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    monitorName = "irrelevant5",
                    integrations = listOf(
                        IntegrationID(IntegrationType.SLACK, "global"),
                        IntegrationID(IntegrationType.EMAIL, "global"),
                    ),
                )
                val icmpMonitor2 = createIcmpMonitor(
                    icmpMonitorRepository,
                    enabled = false,
                    host = "example.com",
                    monitorName = "irrelevant6",
                    uptimeCheckInterval = 120,
                    packetCount = 5,
                    timeoutSeconds = 10,
                    packetLossThreshold = 50,
                    failureCountThreshold = 3L,
                    metricsHistoryEnabled = false,
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

                    val exportedHttpMonitorsRaw = mapper.readTree(responseBody)["http-monitors"].shouldNotBeNull()
                    val parsedHttpMonitors =
                        mapper.convertValue<List<HttpMonitorExportDto>>(exportedHttpMonitorsRaw).shouldNotBeEmpty()

                    parsedHttpMonitors.size shouldBe 2
                    parsedHttpMonitors.forOne { firstMonitor ->
                        firstMonitor.name shouldBe httpMonitor.name
                        firstMonitor.url shouldBe httpMonitor.url
                        firstMonitor.sensitiveUrl shouldBe httpMonitor.sensitiveUrl
                        firstMonitor.uptimeCheckInterval shouldBe httpMonitor.uptimeCheckInterval
                        firstMonitor.enabled shouldBe httpMonitor.enabled
                        firstMonitor.sslCheckEnabled shouldBe httpMonitor.sslCheckEnabled
                        firstMonitor.requestMethod shouldBe httpMonitor.requestMethod
                        firstMonitor.latencyHistoryEnabled shouldBe httpMonitor.latencyHistoryEnabled
                        firstMonitor.forceNoCache shouldBe httpMonitor.forceNoCache
                        firstMonitor.followRedirects shouldBe httpMonitor.followRedirects
                        firstMonitor.sslExpiryThreshold shouldBe httpMonitor.sslExpiryThreshold
                        firstMonitor.failureCountThreshold shouldBe httpMonitor.failureCountThreshold
                    }
                    parsedHttpMonitors.forOne { secondMonitor ->
                        secondMonitor.name shouldBe httpMonitor2.name
                        secondMonitor.url shouldBe httpMonitor2.url
                        secondMonitor.sensitiveUrl shouldBe httpMonitor2.sensitiveUrl
                        secondMonitor.uptimeCheckInterval shouldBe httpMonitor2.uptimeCheckInterval
                        secondMonitor.enabled shouldBe httpMonitor2.enabled
                        secondMonitor.sslCheckEnabled shouldBe httpMonitor2.sslCheckEnabled
                        secondMonitor.requestMethod shouldBe httpMonitor2.requestMethod
                        secondMonitor.latencyHistoryEnabled shouldBe httpMonitor2.latencyHistoryEnabled
                        secondMonitor.forceNoCache shouldBe httpMonitor2.forceNoCache
                        secondMonitor.followRedirects shouldBe httpMonitor2.followRedirects
                        secondMonitor.sslExpiryThreshold shouldBe httpMonitor2.sslExpiryThreshold
                        secondMonitor.failureCountThreshold shouldBe httpMonitor2.failureCountThreshold
                        secondMonitor.expectedStatusCodes shouldBe httpMonitor2.expectedStatusCodes.toSet()
                        secondMonitor.responseTimeThresholdMillis shouldBe httpMonitor2.responseTimeThresholdMillis
                        secondMonitor.expectedKeyword shouldBe httpMonitor2.expectedKeyword
                        secondMonitor.expectedKeywordCaseSensitive shouldBe httpMonitor2.expectedKeywordCaseSensitive
                        secondMonitor.expectedKeywordNegated shouldBe httpMonitor2.expectedKeywordNegated
                        secondMonitor.requestHeaders shouldBe httpMonitor2.requestHeadersAsMap()
                        secondMonitor.expectedHeaders shouldBe httpMonitor2.expectedHeadersAsMap()
                        secondMonitor.requestBody shouldBe httpMonitor2.requestBody
                        secondMonitor.integrations shouldContainExactlyInAnyOrder setOf(
                            IntegrationID(IntegrationType.SLACK, "disabled"),
                            IntegrationID(IntegrationType.DISCORD, "global"),
                        )
                    }

                    val exportedPushMonitorsRaw = mapper.readTree(responseBody)["push-monitors"].shouldNotBeNull()
                    val parsedPushMonitors =
                        mapper.convertValue<List<PushMonitorExportDto>>(exportedPushMonitorsRaw).shouldNotBeEmpty()
                    parsedPushMonitors.size shouldBe 2
                    parsedPushMonitors.forOne { firstMonitor ->
                        firstMonitor.name shouldBe pushMonitor.name
                        firstMonitor.heartbeatInterval shouldBe pushMonitor.heartbeatInterval
                        firstMonitor.gracePeriod shouldBe pushMonitor.gracePeriod
                        firstMonitor.clientSecret shouldBe pushMonitor.clientSecret
                        firstMonitor.enabled shouldBe pushMonitor.enabled
                        firstMonitor.failureCountThreshold shouldBe pushMonitor.failureCountThreshold
                        firstMonitor.integrations shouldContainExactlyInAnyOrder setOf(
                            IntegrationID(IntegrationType.EMAIL, "Global-343"),
                            IntegrationID(IntegrationType.PAGERDUTY, "global"),
                        )
                    }
                    parsedPushMonitors.forOne { secondMonitor ->
                        secondMonitor.name shouldBe pushMonitor2.name
                        secondMonitor.heartbeatInterval shouldBe pushMonitor2.heartbeatInterval
                        secondMonitor.gracePeriod shouldBe pushMonitor2.gracePeriod
                        secondMonitor.clientSecret shouldBe pushMonitor2.clientSecret
                        secondMonitor.enabled shouldBe pushMonitor2.enabled
                        secondMonitor.failureCountThreshold shouldBe pushMonitor2.failureCountThreshold
                        secondMonitor.integrations.shouldBeEmpty()
                    }

                    val exportedIcmpMonitorsRaw = mapper.readTree(responseBody)["icmp-monitors"].shouldNotBeNull()
                    val parsedIcmpMonitors =
                        mapper.convertValue<List<IcmpMonitorExportDto>>(exportedIcmpMonitorsRaw).shouldNotBeEmpty()
                    parsedIcmpMonitors.size shouldBe 2
                    parsedIcmpMonitors.forOne { firstMonitor ->
                        firstMonitor.name shouldBe icmpMonitor.name
                        firstMonitor.host shouldBe icmpMonitor.host
                        firstMonitor.uptimeCheckInterval shouldBe icmpMonitor.uptimeCheckInterval
                        firstMonitor.packetCount shouldBe icmpMonitor.packetCount
                        firstMonitor.timeoutSeconds shouldBe icmpMonitor.timeoutSeconds
                        firstMonitor.packetLossThreshold shouldBe icmpMonitor.packetLossThreshold
                        firstMonitor.failureCountThreshold shouldBe icmpMonitor.failureCountThreshold
                        firstMonitor.enabled shouldBe icmpMonitor.enabled
                        firstMonitor.metricsHistoryEnabled shouldBe icmpMonitor.metricsHistoryEnabled
                        firstMonitor.integrations shouldContainExactlyInAnyOrder setOf(
                            IntegrationID(IntegrationType.SLACK, "global"),
                            IntegrationID(IntegrationType.EMAIL, "global"),
                        )
                    }
                    parsedIcmpMonitors.forOne { secondMonitor ->
                        secondMonitor.name shouldBe icmpMonitor2.name
                        secondMonitor.host shouldBe icmpMonitor2.host
                        secondMonitor.uptimeCheckInterval shouldBe icmpMonitor2.uptimeCheckInterval
                        secondMonitor.packetCount shouldBe icmpMonitor2.packetCount
                        secondMonitor.timeoutSeconds shouldBe icmpMonitor2.timeoutSeconds
                        secondMonitor.packetLossThreshold shouldBe icmpMonitor2.packetLossThreshold
                        secondMonitor.failureCountThreshold shouldBe icmpMonitor2.failureCountThreshold
                        secondMonitor.enabled shouldBe icmpMonitor2.enabled
                        secondMonitor.metricsHistoryEnabled shouldBe icmpMonitor2.metricsHistoryEnabled
                        secondMonitor.integrations.shouldBeEmpty()
                    }
                }
            }

            `when`("there are no monitors in the database") {

                val request = HttpRequest.GET<Any>("/api/v2/monitors/export/yaml").accept(MediaType.APPLICATION_YAML)

                then("it should export an empty monitors list in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedHttpMonitorsRaw = mapper.readTree(responseBody)["http-monitors"].shouldNotBeNull()
                    mapper.convertValue<List<HttpMonitorExportDto>>(exportedHttpMonitorsRaw).shouldBeEmpty()
                    val exportedPushMonitorsRaw = mapper.readTree(responseBody)["push-monitors"].shouldNotBeNull()
                    mapper.convertValue<List<PushMonitorExportDto>>(exportedPushMonitorsRaw).shouldBeEmpty()
                    val exportedIcmpMonitorsRaw = mapper.readTree(responseBody)["icmp-monitors"].shouldNotBeNull()
                    mapper.convertValue<List<IcmpMonitorExportDto>>(exportedIcmpMonitorsRaw).shouldBeEmpty()
                }
            }
        }

        given("MonitorController's importYamlMonitors() endpoint") {

            `when`("a valid YAML file is uploaded") {
                val existingMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "to-be-deleted",
                )

                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(
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
                )

                val multipartBody = MultipartBody.builder()
                    .addPart("file", "monitors.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml?dryRun=false", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should import the monitors and delete missing ones") {
                    val response = client.exchange(request, MonitorImportResultDto::class.java).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body()!!.receivedMonitorCnt shouldBe 1
                    response.body()!!.importedMonitorCnt shouldBe 1
                    response.body()!!.deletedMonitorCount shouldBe 1
                    response.body()!!.dryRun shouldBe false
                    response.body()!!.perTypeResults shouldHaveSize 1
                    response.body()!!.perTypeResults.first().monitorType shouldBe MonitorType.HTTP_SSL
                    response.body()!!.perTypeResults.first().receivedMonitorCnt shouldBe 1
                    response.body()!!.perTypeResults.first().importedMonitorCnt shouldBe 1
                    response.body()!!.perTypeResults.first().deletedMonitorCount shouldBe 1

                    httpMonitorRepository.findById(existingMonitor.id, null) shouldBe null
                    httpMonitorRepository.findByName("imported-http") shouldNotBe null
                }
            }

            `when`("dryRun is true") {
                val existingMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "kept-monitor",
                )

                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "dry-run-http",
                            url = "https://example.com/dry-run",
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

                val multipartBody = MultipartBody.builder()
                    .addPart("file", "monitors.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml?dryRun=true", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return the result without changing the database") {
                    val response = client.exchange(request, MonitorImportResultDto::class.java).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body()!!.receivedMonitorCnt shouldBe 1
                    response.body()!!.importedMonitorCnt shouldBe 1
                    response.body()!!.deletedMonitorCount shouldBe 1
                    response.body()!!.dryRun shouldBe true
                    response.body()!!.perTypeResults shouldHaveSize 1
                    response.body()!!.perTypeResults.first().monitorType shouldBe MonitorType.HTTP_SSL
                    response.body()!!.perTypeResults.first().receivedMonitorCnt shouldBe 1
                    response.body()!!.perTypeResults.first().importedMonitorCnt shouldBe 1
                    response.body()!!.perTypeResults.first().deletedMonitorCount shouldBe 1

                    httpMonitorRepository.findById(existingMonitor.id, null)?.name shouldBe existingMonitor.name
                    httpMonitorRepository.findByName("dry-run-http") shouldBe null
                }
            }

            `when`("a valid YAML file contains multiple monitor types") {
                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "multi-http",
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
                    ),
                    pushMonitors = listOf(
                        PushMonitorExportDto(
                            name = "multi-push",
                            heartbeatInterval = 60,
                            gracePeriod = 30,
                            clientSecret = "ab".repeat(18),
                            enabled = true,
                            integrations = emptySet(),
                            failureCountThreshold = 1,
                        )
                    ),
                    icmpMonitors = listOf(
                        IcmpMonitorExportDto(
                            name = "multi-icmp",
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
                    ),
                )

                val multipartBody = MultipartBody.builder()
                    .addPart("file", "monitors.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml?dryRun=false", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should import all monitor types") {
                    val response = client.exchange(request, MonitorImportResultDto::class.java).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body()!!.receivedMonitorCnt shouldBe 3
                    response.body()!!.importedMonitorCnt shouldBe 3
                    response.body()!!.perTypeResults shouldHaveSize 3
                    response.body()!!.perTypeResults.map { it.monitorType }.toSet() shouldBe setOf(
                        MonitorType.HTTP_SSL,
                        MonitorType.PUSH,
                        MonitorType.ICMP,
                    )
                }
            }

            `when`("the uploaded file is empty") {
                val multipartBody = MultipartBody.builder()
                    .addPart("file", "empty.yml", MediaType.APPLICATION_YAML_TYPE, ByteArray(0))
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return 400 bad request") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.exchange(request, ServiceError::class.java).awaitFirst()
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("the uploaded file exceeds the maximum allowed size of 10 MB") {
                val oversizedContent = ByteArray(10 * 1024 * 1024 + 1)
                val multipartBody = MultipartBody.builder()
                    .addPart("file", "oversized.yml", MediaType.APPLICATION_YAML_TYPE, oversizedContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return 400 bad request") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.exchange(request, ServiceError::class.java).awaitFirst()
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("the uploaded YAML is malformed") {
                val multipartBody = MultipartBody.builder()
                    .addPart("file", "broken.yml", MediaType.APPLICATION_YAML_TYPE, "not: valid: [".toByteArray())
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return 400 bad request") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.exchange(request, ServiceError::class.java).awaitFirst()
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("the uploaded YAML does not contain any monitors") {
                val yamlContent = buildYamlImportContent()

                val multipartBody = MultipartBody.builder()
                    .addPart("file", "empty-monitors.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return 400 bad request") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.exchange(request, ServiceError::class.java).awaitFirst()
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("the uploaded YAML contains an invalid monitor") {
                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "",
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

                val multipartBody = MultipartBody.builder()
                    .addPart("file", "invalid-monitor.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                    .build()

                val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                then("it should return 400 bad request") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.exchange(request, ServiceError::class.java).awaitFirst()
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

        }
    }
}
