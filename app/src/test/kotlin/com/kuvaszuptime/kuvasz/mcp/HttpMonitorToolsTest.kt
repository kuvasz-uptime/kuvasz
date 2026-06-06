package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.models.HttpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.models.HttpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.models.HttpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import io.kotest.inspectors.forOne
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.spec.McpSchema
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.readValue

@MicronautTest
class HttpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client) {

    init {
        given("the HTTP monitor tools") {

            `when`("list-http-monitors is called with monitors in the DB") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool("list-http-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<HttpMonitorDetailsSchema>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<HttpMonitorDetailsSchema>>(
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

                    val details = objectMapper.convertValue<HttpMonitorDetailsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<HttpMonitorDetailsSchema>(result.firstText()) shouldBe details
                }
            }

            `when`("get-http-monitor-details is called with a non-existent ID") {
                val response = callTool("get-http-monitor-details", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
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

                    val created = objectMapper.convertValue<HttpMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    created.name shouldBe "mcp-created-monitor"
                    created.url shouldBe "https://example.com"
                    created.uptimeCheckInterval shouldBe 60

                    objectMapper.readValue<HttpMonitorSchema>(result.firstText()) shouldBe created
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

                then("it should return an invalid-params protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_PARAMS
                }
            }

            `when`("get-http-monitor-stats is called with a valid monitor ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool("get-http-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<HttpMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id
                    stats.latencyHistoryEnabled shouldBe monitor.latencyHistoryEnabled

                    objectMapper.readValue<HttpMonitorStatsSchema>(result.firstText()) shouldBe stats
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
                    objectMapper.convertValue<HttpMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    ).id shouldBe monitor.id
                }
            }

            `when`("get-http-monitor-stats is called with an invalid period string") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool(
                    "get-http-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period")
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("get-http-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool("get-http-monitor-stats", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("toggle-http-monitor is called to disable a monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val response = callTool(
                    "toggle-http-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<HttpMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false

                    objectMapper.readValue<HttpMonitorSchema>(result.firstText()) shouldBe updated
                }
            }

            `when`("toggle-http-monitor is called to enable a monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, enabled = false)
                val response = callTool(
                    "toggle-http-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true") {
                    val updated = objectMapper.convertValue<HttpMonitorSchema>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.enabled shouldBe true
                }
            }

            `when`("toggle-http-monitor is called with a non-existent monitor ID") {
                val response = callTool("toggle-http-monitor", mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }
        }
    }
}
