package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.dataformat.yaml.YAMLMapper

@MicronautTest(environments = ["full-integrations-setup"])
class MonitorControllerImportReadOnlyTest(
    @param:Client("/") private val client: HttpClient,
    private val appConfig: AppConfig,
    private val yamlMapper: YAMLMapper,
) : DatabaseBehaviorSpec() {

    override suspend fun afterSpec(spec: Spec) {
        // Re-enable every monitor write toggle so this spec does not leak disabled state into later specs
        appConfig.enableHttpMonitorExternalWrite()
        appConfig.enablePushMonitorExternalWrite()
        appConfig.enableIcmpMonitorExternalWrite()
    }

    init {

        given("MonitorController's importYamlMonitors() endpoint with a yaml-managed monitor type") {

            `when`("the backup only contains monitors of the yaml-managed type") {
                appConfig.disableHttpMonitorExternalWrite()
                appConfig.enablePushMonitorExternalWrite()
                appConfig.enableIcmpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(httpMonitor("skipped-http"))
                )

                then("it should silently skip the yaml-managed type and return 200 with zero counts") {
                    val response = client.exchange(
                        HttpRequest.POST("/api/v2/monitors/import/yaml", multipartOf(yamlContent))
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.APPLICATION_JSON_TYPE),
                        MonitorImportResultDto::class.java,
                    ).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body().shouldNotBeNull().apply {
                        receivedMonitorCnt shouldBe 0
                        importedMonitorCnt shouldBe 0
                        deletedMonitorCount shouldBe 0
                        perTypeResults.shouldBeEmpty()
                    }
                }
            }

            `when`("the backup contains a mix of yaml-managed and writable types") {
                appConfig.disableHttpMonitorExternalWrite()
                appConfig.enablePushMonitorExternalWrite()
                appConfig.enableIcmpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(httpMonitor("skipped-http")),
                    pushMonitors = listOf(pushMonitor("imported-push")),
                )

                then("it should import only the writable types and return 200") {
                    val response = client.exchange(
                        HttpRequest.POST(
                            "/api/v2/monitors/import/yaml?dryRun=true",
                            multipartOf(yamlContent),
                        ).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.APPLICATION_JSON_TYPE),
                        MonitorImportResultDto::class.java,
                    ).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body().shouldNotBeNull().apply {
                        receivedMonitorCnt shouldBe 1
                        importedMonitorCnt shouldBe 1
                        perTypeResults.map { it.monitorType } shouldBe listOf(
                            com.kuvaszuptime.kuvasz.models.MonitorType.PUSH,
                        )
                    }
                }
            }

            `when`("push monitor writes are yaml-managed and the backup only contains push monitors") {
                appConfig.enableHttpMonitorExternalWrite()
                appConfig.disablePushMonitorExternalWrite()
                appConfig.enableIcmpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    pushMonitors = listOf(pushMonitor("skipped-push"))
                )

                then("it should silently skip push and return 200 with zero counts") {
                    val response = client.exchange(
                        HttpRequest.POST("/api/v2/monitors/import/yaml", multipartOf(yamlContent))
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.APPLICATION_JSON_TYPE),
                        MonitorImportResultDto::class.java,
                    ).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body().shouldNotBeNull().receivedMonitorCnt shouldBe 0
                }
            }

            `when`("ICMP monitor writes are yaml-managed and the backup only contains ICMP monitors") {
                appConfig.enableHttpMonitorExternalWrite()
                appConfig.enablePushMonitorExternalWrite()
                appConfig.disableIcmpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    icmpMonitors = listOf(icmpMonitor("skipped-icmp"))
                )

                then("it should silently skip ICMP and return 200 with zero counts") {
                    val response = client.exchange(
                        HttpRequest.POST("/api/v2/monitors/import/yaml", multipartOf(yamlContent))
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.APPLICATION_JSON_TYPE),
                        MonitorImportResultDto::class.java,
                    ).awaitFirst()

                    response.status shouldBe HttpStatus.OK
                    response.body().shouldNotBeNull().receivedMonitorCnt shouldBe 0
                }
            }

            `when`("the response is an error") {
                then("ServiceError should be returned for non-import related failures") {
                    val unused = shouldThrow<HttpClientResponseException> {
                        client.exchange(
                            HttpRequest.POST(
                                "/api/v2/monitors/import/yaml",
                                multipartOf("not: valid: [".toByteArray()),
                            ).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                                .accept(MediaType.APPLICATION_JSON_TYPE),
                            ServiceError::class.java,
                        ).awaitFirst()
                    }
                    unused.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    private fun multipartOf(content: ByteArray): MultipartBody =
        MultipartBody.builder()
            .addPart("file", "content.yml", MediaType.APPLICATION_YAML_TYPE, content)
            .build()

    private fun httpMonitor(name: String) = HttpMonitorExportDto(
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

    private fun pushMonitor(name: String) = PushMonitorExportDto(
        name = name,
        heartbeatInterval = 60,
        gracePeriod = 30,
        clientSecret = "ab".repeat(18),
        enabled = true,
        integrations = emptySet(),
        failureCountThreshold = 1,
    )

    private fun icmpMonitor(name: String) = IcmpMonitorExportDto(
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

    private fun buildYamlImportContent(
        httpMonitors: List<HttpMonitorExportDto> = emptyList(),
        pushMonitors: List<PushMonitorExportDto> = emptyList(),
        icmpMonitors: List<IcmpMonitorExportDto> = emptyList(),
    ): ByteArray {
        val content = mapOf(
            "http-monitors" to httpMonitors,
            "push-monitors" to pushMonitors,
            "icmp-monitors" to icmpMonitors,
        )

        return yamlMapper.writeValueAsBytes(content)
    }
}
