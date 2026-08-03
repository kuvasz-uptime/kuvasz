package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_TCP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_TCP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_TCP_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_TCP_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_TCP_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_TCP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createTcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import com.kuvaszuptime.kuvasz.testutils.shouldHaveInputValidationError
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest(environments = ["full-integrations-setup"])
class TcpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val tcpMonitorRepository: TcpMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-tcp-monitors tool") {

            `when`("list-tcp-monitors is called with monitors in the DB") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val response = callToolWithMcpClient(LIST_TCP_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<TcpMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<TcpMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-tcp-monitor-details tool") {

            `when`("get-tcp-monitor-details is called with a valid ID") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val response = callToolWithMcpClient(GET_TCP_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<TcpMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name
                    details.port shouldBe monitor.port

                    response.contentAs<TcpMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-tcp-monitor-details is called for a monitor under an active maintenance window") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "tcp-maintenance",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.TCP, monitor.name)),
                )
                val response = callToolWithMcpClient(GET_TCP_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should expose inMaintenance=true and the affecting maintenance window") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<TcpMonitorDetailsSchema>().shouldNotBeNull()
                    details.inMaintenance shouldBe true
                    details.maintenanceWindows.forOne { expectedWindow ->
                        expectedWindow.id shouldBe window.id
                        expectedWindow.name shouldBe window.name
                        expectedWindow.active shouldBe true
                    }
                }
            }

            `when`("get-tcp-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_TCP_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-tcp-monitor tool") {

            `when`("create-tcp-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_TCP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-tcp-monitor",
                        "host" to "10.0.0.1",
                        "port" to 5432,
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<TcpMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-tcp-monitor"
                        host shouldBe "10.0.0.1"
                        port shouldBe 5432
                        uptimeCheckInterval shouldBe 60
                        enabled shouldBe true

                        response.contentAs<TcpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-tcp-monitor is called with an invalid port") {
                val response = callToolWithMcpClient(
                    CREATE_TCP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-tcp-monitor",
                        "host" to "10.0.0.1",
                        "port" to 70000,
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an input schema validation error") {
                    response.shouldHaveInputValidationError("/port: must have a maximum value of 65535")
                }
            }

            `when`("create-tcp-monitor is called with a duplicate name") {
                val existing = createTcpMonitor(tcpMonitorRepository, monitorName = "duplicate-tcp-monitor")
                val response = callTool(
                    CREATE_TCP_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "host" to "10.0.0.2",
                        "port" to 8080,
                        "uptimeCheckInterval" to 60,
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

        given("the get-tcp-monitor-stats tool") {

            `when`("get-tcp-monitor-stats is called with a valid monitor ID") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_TCP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<TcpMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                        response.contentAs<TcpMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-tcp-monitor-stats is called with an invalid period string") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val response = callTool(
                    GET_TCP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration",
                    )
                }
            }

            `when`("get-tcp-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_TCP_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }

            `when`("get-tcp-monitor-stats is called for a monitor with metrics history") {
                val monitor = createTcpMonitor(tcpMonitorRepository, metricsHistoryEnabled = true)
                createTcpMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 10)
                createTcpMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 20)
                createTcpMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = null)

                val response = callToolWithMcpClient(GET_TCP_MONITOR_STATS, mapOf("monitorId" to monitor.id))

                then("it should return populated LatencyStatsSchema and TcpMetricsLogSchema entries") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<TcpMonitorStatsSchema>().shouldNotBeNull()) {
                        latencyStats.shouldNotBeNull()
                        metricsLogs.shouldNotBeEmpty()

                        response.contentAs<TcpMonitorStatsSchema>() shouldBe this
                    }
                }
            }
        }

        given("the toggle-tcp-monitor tool") {

            `when`("toggle-tcp-monitor is called to disable a monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_TCP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<TcpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<TcpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-tcp-monitor is called to enable a monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_TCP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<TcpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<TcpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-tcp-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_TCP_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-tcp-monitor tool") {

            `when`("delete-tcp-monitor is called with a valid monitor ID") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                val response = callToolWithMcpClient(DELETE_TCP_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-tcp-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_TCP_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-tcp-monitors-empty-array"])
class TcpReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the TCP monitor MCP tools when monitors are configured via YAML") {

            `when`("create-tcp-monitor is called") {
                val response = callTool(
                    CREATE_TCP_MONITOR,
                    mapOf(
                        "name" to "readonly-test-tcp-monitor",
                        "host" to "10.0.0.1",
                        "port" to 8080,
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("toggle-tcp-monitor is called") {
                val response = callTool(
                    TOGGLE_TCP_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("delete-tcp-monitor is called") {
                val response = callTool(DELETE_TCP_MONITOR, mapOf("monitorId" to 1L))

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
class TcpMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-tcp-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-tcp-monitor is called for such a monitor") {
                val monitor = createTcpMonitor(tcpMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.TCP, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_TCP_MONITOR, mapOf("monitorId" to monitor.id))

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
