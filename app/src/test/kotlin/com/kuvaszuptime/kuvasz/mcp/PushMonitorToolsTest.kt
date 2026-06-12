package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_PUSH_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_PUSH_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_PUSH_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorStatsSchema
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import io.kotest.inspectors.forOne
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest(environments = ["full-integrations-setup"])
class PushMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val pushMonitorRepository: PushMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-push-monitors tool") {

            `when`("list-push-monitors is called with monitors in the DB") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callToolWithMcpClient(LIST_PUSH_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<PushMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<PushMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-push-monitor-details tool") {

            `when`("get-push-monitor-details is called with a valid ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callToolWithMcpClient(GET_PUSH_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<PushMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    response.contentAs<PushMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-push-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_PUSH_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-push-monitor tool") {

            `when`("create-push-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_PUSH_MONITOR,
                    mapOf(
                        "name" to "mcp-created-push-monitor",
                        "heartbeatInterval" to 300,
                        "clientSecret" to "test-secret-that-is-long-enough-abc123",
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<PushMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-push-monitor"
                        heartbeatInterval shouldBe 300
                        clientSecret shouldBe "test-secret-that-is-long-enough-abc123"
                        enabled shouldBe true

                        response.contentAs<PushMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-push-monitor is called with an invalid heartbeatInterval") {
                val response = callTool(
                    CREATE_PUSH_MONITOR,
                    mapOf(
                        "name" to "mcp-created-push-monitor",
                        "heartbeatInterval" to 1,
                        "clientSecret" to "test-secret-that-is-long-enough-abc123",
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "Heartbeat interval must be at least 10 seconds",
                    )
                }
            }

            `when`("create-push-monitor is called with a duplicate name") {
                val existing = createPushMonitor(pushMonitorRepository, monitorName = "duplicate-push-monitor")
                val response = callTool(
                    CREATE_PUSH_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "heartbeatInterval" to 300,
                        "clientSecret" to "test-secret-that-is-long-enough-abc123",
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "There is already a monitor with the given name",
                    )
                }
            }
        }

        given("the get-push-monitor-stats tool") {

            `when`("get-push-monitor-stats is called with a valid monitor ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_PUSH_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<PushMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id

                        response.contentAs<PushMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-push-monitor-stats is called with an invalid period string") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callTool(
                    GET_PUSH_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration",
                    )
                }
            }

            `when`("get-push-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_PUSH_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the toggle-push-monitor tool") {

            `when`("toggle-push-monitor is called to disable a monitor") {
                val monitor = createPushMonitor(pushMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_PUSH_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<PushMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<PushMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-push-monitor is called to enable a monitor") {
                val monitor = createPushMonitor(pushMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_PUSH_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<PushMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<PushMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-push-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_PUSH_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-push-monitor tool") {

            `when`("delete-push-monitor is called with a valid monitor ID") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val response = callToolWithMcpClient(DELETE_PUSH_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-push-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_PUSH_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-push-monitors-empty-array"])
class PushReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the push monitor MCP tools when monitors are configured via YAML") {

            `when`("create-push-monitor is called") {
                val response = callTool(
                    CREATE_PUSH_MONITOR,
                    mapOf(
                        "name" to "readonly-test-push-monitor",
                        "heartbeatInterval" to 300,
                        "clientSecret" to "test-secret-that-is-long-enough-abc123",
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("toggle-push-monitor is called") {
                val response = callTool(
                    TOGGLE_PUSH_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("delete-push-monitor is called") {
                val response = callTool(DELETE_PUSH_MONITOR, mapOf("monitorId" to 1L))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }
        }
    }
}

@MicronautTest(environments = ["full-integrations-setup"])
class PushMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val pushMonitorRepository: PushMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-push-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-push-monitor is called for such a monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_PUSH_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Monitor cannot be deleted because it is referenced by a read-only status page"
                    )
                }
            }
        }
    }
}
