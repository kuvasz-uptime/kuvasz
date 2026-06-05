package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import io.kotest.inspectors.forOne
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class HttpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client, objectMapper) {

    init {
        given("the HTTP monitor tools") {

            `when`("list-http-monitors is called with monitors in the DB") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool("list-http-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<HttpMonitorDetailsDto>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<HttpMonitorDetailsDto>>(
                        objectMapper.readTree(result.firstText())["monitors"]
                    ) shouldBe monitors
                }
            }

            `when`("get-http-monitor-details is called with a valid ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool("get-http-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val details = objectMapper.convertValue<HttpMonitorDetailsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<HttpMonitorDetailsDto>(result.firstText()) shouldBe details
                }
            }

            `when`("get-http-monitor-details is called with a non-existent ID") {
                val response = callTool("get-http-monitor-details", mapOf("monitorId" to -999L))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.firstText() shouldContain "-999"
                    result.structuredContent.shouldBeNull()
                }
            }

            `when`("create-http-monitor is called with valid input") {
                val response = callTool(
                    "create-http-monitor",
                    mapOf(
                        "name" to "mcp-created-monitor",
                        "url" to "https://example.com",
                        "uptimeCheckInterval" to 60,
                        "enabled" to false,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val created = objectMapper.convertValue<HttpMonitorDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    created.name shouldBe "mcp-created-monitor"
                    created.url shouldBe "https://example.com"
                    created.uptimeCheckInterval shouldBe 60

                    objectMapper.readValue<HttpMonitorDto>(result.firstText()) shouldBe created
                }
            }

            `when`("create-http-monitor is called with a duplicate name") {
                val existing = createHttpMonitor(httpMonitorRepository, monitorName = "duplicate-monitor")
                val response = callTool(
                    "create-http-monitor",
                    mapOf(
                        "name" to existing.name,
                        "url" to "https://other.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return isError true with no structured content and no protocol error") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.structuredContent.shouldBeNull()
                    response.error.shouldBeNull()
                }
            }

            `when`("get-http-monitor-stats is called with a valid monitor ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool("get-http-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<HttpMonitorStatsDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id
                    stats.latencyHistoryEnabled shouldBe monitor.latencyHistoryEnabled

                    objectMapper.readValue<HttpMonitorStatsDto>(result.firstText()) shouldBe stats
                }
            }

            `when`("get-http-monitor-stats is called with a custom ISO 8601 period") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool(
                    "get-http-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "PT12H")
                )

                then("it should return stats for the requested period") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false
                    objectMapper.convertValue<HttpMonitorStatsDto>(
                        result.structuredContent.shouldNotBeNull()
                    ).id shouldBe monitor.id
                }
            }

            `when`("get-http-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool("get-http-monitor-stats", mapOf("monitorId" to -999L))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.firstText() shouldContain "-999"
                    result.structuredContent.shouldBeNull()
                }
            }

            `when`("update-http-monitor is called with a valid partial patch") {
                val monitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val response = callTool(
                    "update-http-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false, "name" to "updated-name")
                )

                then("it should return the updated monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<HttpMonitorDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false
                    updated.name shouldBe "updated-name"

                    objectMapper.readValue<HttpMonitorDto>(result.firstText()) shouldBe updated
                }
            }

            `when`("update-http-monitor omits a field") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    uptimeCheckInterval = 60,
                    expectedKeyword = "hello",
                )
                val response = callTool(
                    "update-http-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("the omitted fields should keep their current values") {
                    val updated = objectMapper.convertValue<HttpMonitorDto>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.uptimeCheckInterval shouldBe 60
                    updated.expectedKeyword shouldBe "hello"
                }
            }

            `when`("update-http-monitor sets a nullable field to null explicitly") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    expectedKeyword = "hello",
                    responseTimeThresholdMillis = 500,
                )
                val response = callTool(
                    "update-http-monitor",
                    mapOf("monitorId" to monitor.id, "expectedKeyword" to null, "responseTimeThresholdMillis" to null)
                )

                then("the nullable fields should be cleared") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<HttpMonitorDto>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.expectedKeyword.shouldBeNull()
                    updated.responseTimeThresholdMillis.shouldBeNull()
                }
            }

            `when`("update-http-monitor is called with a non-existent monitor ID") {
                val response = callTool("update-http-monitor", mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.firstText() shouldContain "-999"
                    result.structuredContent.shouldBeNull()
                }
            }

            `when`("update-http-monitor is called without monitorId") {
                val response = callTool("update-http-monitor", mapOf("enabled" to false))

                then("it should return isError true with no structured content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe true
                    result.structuredContent.shouldBeNull()
                }
            }
        }
    }
}
