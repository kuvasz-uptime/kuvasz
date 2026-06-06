package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldNotBeEmpty
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

@MicronautTest(environments = ["full-integrations-setup"])
class PushMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val pushMonitorRepository: PushMonitorRepository,
    private val objectMapper: ObjectMapper,
) : McpToolTest(client) {

    init {
        given("the PUSH monitor tools") {

            `when`("list-push-monitors is called with monitors in the DB") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("list-push-monitors")

                then("it should return the list in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val monitors = objectMapper.convertValue<List<PushMonitorDetailsSchema>>(
                        result.structuredContent.shouldNotBeNull()["monitors"]
                    )
                    monitors.forOne { it.name shouldBe monitor.name }

                    objectMapper.convertValue<List<PushMonitorDetailsSchema>>(
                        objectMapper.readTree(result.firstText())["monitors"]
                    ) shouldBe monitors
                }
            }

            `when`("get-push-monitor-details is called with a valid ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("get-push-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val details = objectMapper.convertValue<PushMonitorDetailsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    objectMapper.readValue<PushMonitorDetailsSchema>(result.firstText()) shouldBe details
                }
            }

            `when`("get-push-monitor-details is called with a non-existent ID") {
                val response = callTool("get-push-monitor-details", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("create-push-monitor is called with valid input") {
                val response = callTool(
                    "create-push-monitor",
                    mapOf(
                        "name" to "mcp-created-push-monitor",
                        "heartbeatInterval" to 300,
                        "gracePeriod" to 60,
                        "clientSecret" to "test-secret-abc123",
                        "enabled" to false,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val created = objectMapper.convertValue<PushMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    created.name shouldBe "mcp-created-push-monitor"
                    created.heartbeatInterval shouldBe 300
                    created.gracePeriod shouldBe 60
                    created.clientSecret shouldBe "test-secret-abc123"

                    objectMapper.readValue<PushMonitorSchema>(result.firstText()) shouldBe created
                }
            }

            `when`("create-push-monitor is called with a duplicate name") {
                val existing = createPushMonitor(pushMonitorRepository, monitorName = "duplicate-push-monitor")
                val response = callTool(
                    "create-push-monitor",
                    mapOf(
                        "name" to existing.name,
                        "heartbeatInterval" to 300,
                        "clientSecret" to "other-secret",
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_PARAMS
                }
            }

            `when`("get-push-monitor-stats is called with a valid monitor ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool("get-push-monitor-stats", mapOf("monitorId" to monitor.id))

                then("it should return stats in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val stats = objectMapper.convertValue<PushMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    stats.id shouldBe monitor.id

                    objectMapper.readValue<PushMonitorStatsSchema>(result.firstText()) shouldBe stats
                }
            }

            `when`("get-push-monitor-stats is called with a custom ISO 8601 period") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool(
                    "get-push-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "PT12H")
                )

                then("it should return stats for the requested period") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false
                    objectMapper.convertValue<PushMonitorStatsSchema>(
                        result.structuredContent.shouldNotBeNull()
                    ).id shouldBe monitor.id
                }
            }

            `when`("get-push-monitor-stats is called with an invalid period string") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool(
                    "get-push-monitor-stats",
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period")
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.INVALID_REQUEST
                }
            }

            `when`("get-push-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool("get-push-monitor-stats", mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("toggle-push-monitor is called to disable a monitor") {
                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val response = callTool(
                    "toggle-push-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    val result = response.result.shouldNotBeNull()
                    result.isError shouldBe false

                    val updated = objectMapper.convertValue<PushMonitorSchema>(
                        result.structuredContent.shouldNotBeNull()
                    )
                    updated.id shouldBe monitor.id
                    updated.enabled shouldBe false

                    objectMapper.readValue<PushMonitorSchema>(result.firstText()) shouldBe updated
                }
            }

            `when`("toggle-push-monitor is called to enable a monitor") {
                val monitor = createPushMonitor(pushMonitorRepository, enabled = false)
                val response = callTool(
                    "toggle-push-monitor",
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true") {
                    val updated = objectMapper.convertValue<PushMonitorSchema>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    updated.enabled shouldBe true
                }
            }

            `when`("toggle-push-monitor is called with a non-existent monitor ID") {
                val response = callTool("toggle-push-monitor", mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.result.shouldBeNull()
                    response.error.shouldNotBeNull().code shouldBe McpSchema.ErrorCodes.RESOURCE_NOT_FOUND
                }
            }

            `when`("get-push-monitor-details is called for a monitor with integrations") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    integrations = listOf(IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")),
                )
                val response = callTool("get-push-monitor-details", mapOf("monitorId" to monitor.id))

                then("it should return populated effectiveIntegrations with IntegrationDetailsSchema entries") {
                    val details = objectMapper.convertValue<PushMonitorDetailsSchema>(
                        response.result.shouldNotBeNull().structuredContent.shouldNotBeNull()
                    )
                    val effectiveIntegrations = details.effectiveIntegrations.shouldNotBeEmpty()
                    effectiveIntegrations.forOne { integration ->
                        integration.id shouldBe "slack:test_implicitly_enabled"
                        integration.type shouldBe IntegrationType.SLACK
                        integration.name shouldBe "test_implicitly_enabled"
                        integration.enabled shouldBe true
                        integration.global shouldBe false
                    }
                }
            }
        }
    }
}
