package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.models.IcmpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.models.IcmpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.models.IcmpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
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
class IcmpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client) {

    init {
        given("the MCP endpoint") {

            `when`("list-icmp-monitors is called with monitors in the DB") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("list-icmp-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<IcmpMonitorDetailsSchema>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<IcmpMonitorDetailsSchema>>(
                        objectMapper.readTree(result.firstText())["monitors"]
                    ) shouldBe monitors
                }
            }

            `when`("get-icmp-monitor-details is called with a valid ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("get-icmp-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val details = objectMapper.convertValue<IcmpMonitorDetailsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<IcmpMonitorDetailsSchema>(result.firstText()) shouldBe details
                }
            }

            `when`("get-icmp-monitor-details is called with a non-existent ID") {
                val response = callTool("get-icmp-monitor-details", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("create-icmp-monitor is called with valid input") {
                val response = callTool(
                    "create-icmp-monitor",
                    mapOf(
                        "name" to "mcp-created-icmp-monitor",
                        "host" to "10.0.0.1",
                        "uptimeCheckInterval" to 60,
                        "enabled" to false,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val created = objectMapper.convertValue<IcmpMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    created.name shouldBe "mcp-created-icmp-monitor"
                    created.host shouldBe "10.0.0.1"
                    created.uptimeCheckInterval shouldBe 60

                    objectMapper.readValue<IcmpMonitorSchema>(result.firstText()) shouldBe created
                }
            }

            `when`("create-icmp-monitor is called with a duplicate name") {
                val existing = createIcmpMonitor(icmpMonitorRepository, monitorName = "duplicate-icmp-monitor")
                val response = callTool(
                    "create-icmp-monitor",
                    mapOf(
                        "name" to existing.name,
                        "host" to "10.0.0.2",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_PARAMS
                }
            }

            `when`("get-icmp-monitor-stats is called with a valid monitor ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool("get-icmp-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<IcmpMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id
                    stats.metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                    objectMapper.readValue<IcmpMonitorStatsSchema>(result.firstText()) shouldBe stats
                }
            }

            `when`("get-icmp-monitor-stats is called with a custom ISO 8601 period") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool(
                    "get-icmp-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "PT12H")
                )

                then("it should return stats for the requested period") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false
                    objectMapper.convertValue<IcmpMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    ).id shouldBe monitor.id
                }
            }

            `when`("get-icmp-monitor-stats is called with an invalid period string") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool(
                    "get-icmp-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period")
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("get-icmp-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool("get-icmp-monitor-stats", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("toggle-icmp-monitor is called to disable a monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val response = callTool(
                    "toggle-icmp-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<IcmpMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false

                    objectMapper.readValue<IcmpMonitorSchema>(result.firstText()) shouldBe updated
                }
            }

            `when`("toggle-icmp-monitor is called to enable a monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                val response = callTool(
                    "toggle-icmp-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true") {
                    val updated = objectMapper.convertValue<IcmpMonitorSchema>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.enabled shouldBe true
                }
            }

            `when`("toggle-icmp-monitor is called with a non-existent monitor ID") {
                val response = callTool("toggle-icmp-monitor", mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }
        }
    }
}
