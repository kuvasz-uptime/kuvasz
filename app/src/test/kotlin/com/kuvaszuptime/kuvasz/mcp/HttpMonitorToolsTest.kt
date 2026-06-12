package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_HTTP_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_HTTP_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_HTTP_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest(environments = ["full-integrations-setup"])
class HttpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-http-monitors tool") {

            `when`("list-http-monitors is called with monitors in the DB") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callToolWithMcpClient(LIST_HTTP_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<HttpMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<HttpMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-http-monitor-details tool") {

            `when`("get-http-monitor-details is called with a valid ID") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    integrations = listOf(IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")),
                )
                val response = callToolWithMcpClient(GET_HTTP_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<HttpMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name
                    details.integrations shouldHaveSingleElement "slack:test_implicitly_enabled"

                    response.contentAs<HttpMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-http-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_HTTP_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-http-monitor tool") {

            `when`("create-http-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_HTTP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-monitor",
                        "url" to "https://example.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<HttpMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-monitor"
                        url shouldBe "https://example.com"
                        uptimeCheckInterval shouldBe 60
                        enabled shouldBe true

                        response.contentAs<HttpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-http-monitor is called with an invalid input") {
                val response = callTool(
                    CREATE_HTTP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-monitor",
                        "url" to "https://example.com",
                        "uptimeCheckInterval" to -1,
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "Uptime check interval must be at least 5 seconds",
                    )
                }
            }

            `when`("create-http-monitor is called with a duplicate name") {
                val existing = createHttpMonitor(httpMonitorRepository, monitorName = "duplicate-monitor")
                val response = callTool(
                    CREATE_HTTP_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "url" to "https://other.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "There is already a monitor with the given name"
                    )
                }
            }
        }

        given("the get-http-monitor-stats tool") {

            `when`("get-http-monitor-stats is called with a valid monitor ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_HTTP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<HttpMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        latencyHistoryEnabled shouldBe monitor.latencyHistoryEnabled
                        period shouldBe "PT12H"
                        response.contentAs<HttpMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-http-monitor-stats is called with an invalid period string") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callTool(
                    GET_HTTP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration"
                    )
                }
            }

            `when`("get-http-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_HTTP_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }

            `when`("get-http-monitor-stats is called for a monitor with latency history") {
                val monitor = createHttpMonitor(httpMonitorRepository, latencyHistoryEnabled = true)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 100)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 200)
                latencyLogRepository.insertLatencyForMonitor(monitor.id, 300)

                val response = callToolWithMcpClient(GET_HTTP_MONITOR_STATS, mapOf("monitorId" to monitor.id))

                then("it should return populated LatencyStatsSchema and LatencyLogSchema entries") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<HttpMonitorStatsSchema>().shouldNotBeNull()) {
                        with(latencyStats.shouldNotBeNull()) {
                            averageLatencyInMs.shouldNotBeNull()
                            minLatencyInMs.shouldNotBeNull()
                            maxLatencyInMs.shouldNotBeNull()
                        }
                        latencyLogs.first().latencyInMs shouldBe 300

                        response.contentAs<HttpMonitorStatsSchema>() shouldBe this
                    }
                }
            }
        }

        given("the toggle-http-monitor tool") {

            `when`("toggle-http-monitor is called to disable a monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_HTTP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<HttpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<HttpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-http-monitor is called to enable a monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_HTTP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false
                    with(response.structuredContentAs<HttpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<HttpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-http-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_HTTP_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-http-monitor tool") {

            `when`("delete-http-monitor is called with a valid monitor ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val response = callToolWithMcpClient(DELETE_HTTP_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-http-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_HTTP_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-monitors-empty-array"])
class HttpReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the HTTP monitor MCP tools when monitors are configured via YAML") {

            `when`("create-http-monitor is called") {
                val response = callTool(
                    CREATE_HTTP_MONITOR,
                    mapOf(
                        "name" to "readonly-test-monitor",
                        "url" to "https://example.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file"
                    )
                }
            }

            `when`("toggle-http-monitor is called") {
                val response = callTool(
                    TOGGLE_HTTP_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file"
                    )
                }
            }

            `when`("delete-http-monitor is called") {
                val response = callTool(DELETE_HTTP_MONITOR, mapOf("monitorId" to 1L))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file"
                    )
                }
            }
        }
    }
}

@MicronautTest(environments = ["full-integrations-setup"])
class HttpMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-http-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-http-monitor is called for such a monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_HTTP_MONITOR, mapOf("monitorId" to monitor.id))

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
