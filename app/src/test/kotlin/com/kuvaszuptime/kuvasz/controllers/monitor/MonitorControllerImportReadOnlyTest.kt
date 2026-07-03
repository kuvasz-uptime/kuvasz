package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
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
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule

@MicronautTest(environments = ["full-integrations-setup"])
class MonitorControllerImportReadOnlyTest(
    @param:Client("/") private val client: HttpClient,
    private val appConfig: AppConfig,
) : DatabaseBehaviorSpec() {

    override suspend fun afterSpec(spec: Spec) {
        // Re-enable HTTP monitor writes so this spec does not leak disabled state into later specs
        appConfig.enableHttpMonitorExternalWrite()
    }

    init {

        given("MonitorController's importYamlMonitors() endpoint in read-only mode") {

            `when`("monitors are in read-only mode") {
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

            val multipartBody = MultipartBody.builder()
                .addPart("file", "read-only.yml", MediaType.APPLICATION_YAML_TYPE, yamlContent)
                .build()

            val request = HttpRequest.POST("/api/v2/monitors/import/yaml", multipartBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE)

            then("it should return 405 method not allowed") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, com.kuvaszuptime.kuvasz.models.ServiceError::class.java).awaitFirst()
                }
                response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
            }
        }
        }
    }

    private fun buildYamlImportContent(
        httpMonitors: List<HttpMonitorExportDto> = emptyList(),
    ): ByteArray {
        val importMapper = YAMLMapper.builder()
            .addModules(kotlinModule())
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .build()

        val content = mapOf(
            "http-monitors" to httpMonitors,
            "push-monitors" to emptyList<PushMonitorExportDto>(),
            "icmp-monitors" to emptyList<IcmpMonitorExportDto>(),
        )

        return importMapper.writeValueAsBytes(content)
    }
}
