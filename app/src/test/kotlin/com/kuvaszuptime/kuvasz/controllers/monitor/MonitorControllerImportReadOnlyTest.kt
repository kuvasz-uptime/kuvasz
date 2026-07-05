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

        given("MonitorController's importYamlMonitors() endpoint in read-only mode") {

            `when`("HTTP monitor writes are disabled and the backup contains HTTP monitors") {
                appConfig.disableHttpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    httpMonitors = listOf(
                        HttpMonitorExportDto(
                            name = "read-only-http",
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

                then("it should return 405 method not allowed") {
                    postImportExpectingMethodNotAllowed(yamlContent)
                }
            }

            `when`("push monitor writes are disabled and the backup contains push monitors") {
                appConfig.disablePushMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    pushMonitors = listOf(
                        PushMonitorExportDto(
                            name = "read-only-push",
                            heartbeatInterval = 60,
                            gracePeriod = 30,
                            clientSecret = "ab".repeat(18),
                            enabled = true,
                            integrations = emptySet(),
                            failureCountThreshold = 1,
                        )
                    )
                )

                then("it should return 405 method not allowed") {
                    postImportExpectingMethodNotAllowed(yamlContent)
                }
            }

            `when`("ICMP monitor writes are disabled and the backup contains ICMP monitors") {
                appConfig.disableIcmpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    icmpMonitors = listOf(
                        IcmpMonitorExportDto(
                            name = "read-only-icmp",
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

                then("it should return 405 method not allowed") {
                    postImportExpectingMethodNotAllowed(yamlContent)
                }
            }

            `when`("HTTP monitor writes are disabled but the backup only contains push monitors") {
                appConfig.enablePushMonitorExternalWrite()
                appConfig.enableIcmpMonitorExternalWrite()
                appConfig.disableHttpMonitorExternalWrite()

                val yamlContent = buildYamlImportContent(
                    pushMonitors = listOf(
                        PushMonitorExportDto(
                            name = "writable-push",
                            heartbeatInterval = 60,
                            gracePeriod = 30,
                            clientSecret = "ab".repeat(18),
                            enabled = true,
                            integrations = emptySet(),
                            failureCountThreshold = 1,
                        )
                    )
                )

                then("it should still import the writable types") {
                    val multipartBody = MultipartBody.builder()
                        .addPart("file", "mixed.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                        .build()
                    val request = HttpRequest.POST("/api/v2/monitors/import/yaml?dryRun=true", multipartBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE)

                    val response = client.exchange(request, MonitorImportResultDto::class.java).awaitFirst()
                    response.status shouldBe HttpStatus.OK
                    response.body()!!.receivedMonitorCnt shouldBe 1
                }
            }
        }
    }

    private suspend fun postImportExpectingMethodNotAllowed(yamlContent: ByteArray) {
        val multipartBody = MultipartBody.builder()
            .addPart("file", "read-only.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
            .build()

        val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE)

        val response = shouldThrow<HttpClientResponseException> {
            client.exchange(request, ServiceError::class.java).awaitFirst()
        }
        response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
    }

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
