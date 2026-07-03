package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.http.expectedHeadersAsMap
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest(environments = ["full-integrations-setup"])
class MonitorRoundTripE2ETest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("a real export -> import round trip via the API") {

            `when`("monitors are exported, wiped, then restored from the exported bytes") {
                val httpMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = false,
                    sensitiveUrl = true,
                    uptimeCheckInterval = 23234,
                    monitorName = "roundtrip-http",
                    sslExpiryThreshold = 15,
                    failureCountThreshold = 5,
                    expectedStatusCodes = setOf(200, 404),
                    responseTimeThresholdMillis = 1400,
                    expectedKeyword = "somethingExpected",
                    expectedKeywordCaseSensitive = true,
                    expectedKeywordNegated = false,
                    requestHeaders = mapOf("X-Test-Header" to "TestValue"),
                    expectedHeaders = mapOf("X-Expected-Header" to "ExpectedValue"),
                    requestBody = "{\"key\": \"value\"}",
                    integrations = listOf(IntegrationID(IntegrationType.SLACK, "disabled")),
                )
                val pushMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    heartbeatInterval = 12345,
                    monitorName = "roundtrip-push",
                    gracePeriod = 54321,
                    clientSecret = "ab".repeat(18),
                    failureCountThreshold = 3,
                )
                val icmpMonitor = createIcmpMonitor(
                    icmpMonitorRepository,
                    enabled = false,
                    host = "example.com",
                    monitorName = "roundtrip-icmp",
                    uptimeCheckInterval = 120,
                    packetCount = 5,
                    timeoutSeconds = 10,
                    packetLossThreshold = 50,
                    failureCountThreshold = 3L,
                    metricsHistoryEnabled = false,
                )

                // 1) Real export via the API - the actual bytes the feature must be able to consume
                val exportBytes = client.exchange(
                    HttpRequest.GET<Any>("/api/v2/monitors/export/yaml").accept(MediaType.APPLICATION_YAML),
                    ByteArray::class.java,
                ).awaitFirst().body().shouldNotBeNull()

                // 2) Wipe the DB to prove the import truly restores everything from scratch
                httpMonitorRepository.deleteById(httpMonitor.id, dslContext)
                pushMonitorRepository.deleteById(pushMonitor.id, dslContext)
                icmpMonitorRepository.deleteById(icmpMonitor.id, dslContext)
                httpMonitorRepository.findByName("roundtrip-http").shouldBeNull()
                pushMonitorRepository.findByName("roundtrip-push").shouldBeNull()
                icmpMonitorRepository.findByName("roundtrip-icmp").shouldBeNull()

                // 3) Restore from the exported bytes via the real import API
                val multipartBody = MultipartBody.builder()
                    .addPart("file", "kuvasz-monitors-export.yml", MediaType.APPLICATION_YAML_TYPE, exportBytes)
                    .build()
                val importRequest = HttpRequest.POST("/api/v2/monitors/import/yaml?dryRun=false", multipartBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE)

                val response = client.exchange(importRequest, MonitorImportResultDto::class.java).awaitFirst()
                val result = response.body().shouldNotBeNull()

                then("the imported monitors should match the originals field-by-field") {
                    response.status shouldBe HttpStatus.OK
                    result.receivedMonitorCnt shouldBe 3
                    result.importedMonitorCnt shouldBe 3
                    result.deletedMonitorCount shouldBe 0
                    result.dryRun shouldBe false

                    val restoredHttp = httpMonitorRepository.findByName("roundtrip-http").shouldNotBeNull()
                    restoredHttp.url shouldBe httpMonitor.url
                    restoredHttp.sensitiveUrl shouldBe httpMonitor.sensitiveUrl
                    restoredHttp.uptimeCheckInterval shouldBe httpMonitor.uptimeCheckInterval
                    restoredHttp.enabled shouldBe httpMonitor.enabled
                    restoredHttp.sslCheckEnabled shouldBe httpMonitor.sslCheckEnabled
                    restoredHttp.latencyHistoryEnabled shouldBe httpMonitor.latencyHistoryEnabled
                    restoredHttp.forceNoCache shouldBe httpMonitor.forceNoCache
                    restoredHttp.followRedirects shouldBe httpMonitor.followRedirects
                    restoredHttp.sslExpiryThreshold shouldBe httpMonitor.sslExpiryThreshold
                    restoredHttp.failureCountThreshold shouldBe httpMonitor.failureCountThreshold
                    restoredHttp.expectedStatusCodes.toSet() shouldBe httpMonitor.expectedStatusCodes.toSet()
                    restoredHttp.responseTimeThresholdMillis shouldBe httpMonitor.responseTimeThresholdMillis
                    restoredHttp.expectedKeyword shouldBe httpMonitor.expectedKeyword
                    restoredHttp.expectedKeywordCaseSensitive shouldBe httpMonitor.expectedKeywordCaseSensitive
                    restoredHttp.expectedKeywordNegated shouldBe httpMonitor.expectedKeywordNegated
                    restoredHttp.requestHeadersAsMap() shouldBe httpMonitor.requestHeadersAsMap()
                    restoredHttp.expectedHeadersAsMap() shouldBe httpMonitor.expectedHeadersAsMap()
                    restoredHttp.requestBody shouldBe httpMonitor.requestBody

                    val restoredPush = pushMonitorRepository.findByName("roundtrip-push").shouldNotBeNull()
                    restoredPush.heartbeatInterval shouldBe pushMonitor.heartbeatInterval
                    restoredPush.gracePeriod shouldBe pushMonitor.gracePeriod
                    restoredPush.clientSecret shouldBe pushMonitor.clientSecret
                    restoredPush.enabled shouldBe pushMonitor.enabled
                    restoredPush.failureCountThreshold shouldBe pushMonitor.failureCountThreshold

                    val restoredIcmp = icmpMonitorRepository.findByName("roundtrip-icmp").shouldNotBeNull()
                    restoredIcmp.host shouldBe icmpMonitor.host
                    restoredIcmp.uptimeCheckInterval shouldBe icmpMonitor.uptimeCheckInterval
                    restoredIcmp.packetCount shouldBe icmpMonitor.packetCount
                    restoredIcmp.timeoutSeconds shouldBe icmpMonitor.timeoutSeconds
                    restoredIcmp.packetLossThreshold shouldBe icmpMonitor.packetLossThreshold
                    restoredIcmp.failureCountThreshold shouldBe icmpMonitor.failureCountThreshold
                    restoredIcmp.enabled shouldBe icmpMonitor.enabled
                    restoredIcmp.metricsHistoryEnabled shouldBe icmpMonitor.metricsHistoryEnabled
                }
            }
        }
    }
}
