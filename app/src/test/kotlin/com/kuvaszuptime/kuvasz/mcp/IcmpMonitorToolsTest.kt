package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_ICMP_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_ICMP_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_ICMP_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createIcmpMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
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
class IcmpMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-icmp-monitors tool") {

            `when`("list-icmp-monitors is called with monitors in the DB") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callToolWithMcpClient(LIST_ICMP_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<IcmpMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<IcmpMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-icmp-monitor-details tool") {

            `when`("get-icmp-monitor-details is called with a valid ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callToolWithMcpClient(GET_ICMP_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<IcmpMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name

                    response.contentAs<IcmpMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-icmp-monitor-details is called for a monitor under an active maintenance window") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "icmp-maintenance",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name)),
                )
                val response = callToolWithMcpClient(GET_ICMP_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should expose inMaintenance=true and the affecting maintenance window") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<IcmpMonitorDetailsSchema>().shouldNotBeNull()
                    details.inMaintenance shouldBe true
                    details.maintenanceWindows.forOne { expectedWindow ->
                        expectedWindow.id shouldBe window.id
                        expectedWindow.name shouldBe window.name
                        expectedWindow.active shouldBe true
                    }
                }
            }

            `when`("get-icmp-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_ICMP_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-icmp-monitor tool") {

            `when`("create-icmp-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_ICMP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-icmp-monitor",
                        "host" to "10.0.0.1",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IcmpMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-icmp-monitor"
                        host shouldBe "10.0.0.1"
                        uptimeCheckInterval shouldBe 60
                        enabled shouldBe true

                        response.contentAs<IcmpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-icmp-monitor is called with an invalid uptimeCheckInterval") {
                val response = callTool(
                    CREATE_ICMP_MONITOR,
                    mapOf(
                        "name" to "mcp-created-icmp-monitor",
                        "host" to "10.0.0.1",
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

            `when`("create-icmp-monitor is called with a duplicate name") {
                val existing = createIcmpMonitor(icmpMonitorRepository, monitorName = "duplicate-icmp-monitor")
                val response = callTool(
                    CREATE_ICMP_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "host" to "10.0.0.2",
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

        given("the get-icmp-monitor-stats tool") {

            `when`("get-icmp-monitor-stats is called with a valid monitor ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_ICMP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IcmpMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                        response.contentAs<IcmpMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-icmp-monitor-stats is called with an invalid period string") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callTool(
                    GET_ICMP_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration",
                    )
                }
            }

            `when`("get-icmp-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_ICMP_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }

            `when`("get-icmp-monitor-stats is called for a monitor with metrics history") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, metricsHistoryEnabled = true)
                createIcmpMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 10, packetLossPercentage = 0)
                createIcmpMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 20, packetLossPercentage = 0)
                createIcmpMetricsLogRecord(
                    dslContext, monitorId = monitor.id, latencyMs = null, packetLossPercentage = 100
                )

                val response = callToolWithMcpClient(GET_ICMP_MONITOR_STATS, mapOf("monitorId" to monitor.id))

                then("it should return populated PacketLossStatsSchema and IcmpMetricsLogSchema entries") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IcmpMonitorStatsSchema>().shouldNotBeNull()) {
                        with(packetLossStats.shouldNotBeNull()) {
                            averagePacketLossPercentage.shouldNotBeNull()
                            minPacketLossPercentage.shouldNotBeNull()
                            maxPacketLossPercentage.shouldNotBeNull()
                        }
                        latencyStats.shouldNotBeNull()
                        metricsLogs.shouldNotBeEmpty()

                        response.contentAs<IcmpMonitorStatsSchema>() shouldBe this
                    }
                }
            }
        }

        given("the toggle-icmp-monitor tool") {

            `when`("toggle-icmp-monitor is called to disable a monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_ICMP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IcmpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<IcmpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-icmp-monitor is called to enable a monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_ICMP_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IcmpMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<IcmpMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-icmp-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_ICMP_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-icmp-monitor tool") {

            `when`("delete-icmp-monitor is called with a valid monitor ID") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val response = callToolWithMcpClient(DELETE_ICMP_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-icmp-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_ICMP_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-icmp-monitors-empty-array"])
class IcmpReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the ICMP monitor MCP tools when monitors are configured via YAML") {

            `when`("create-icmp-monitor is called") {
                val response = callTool(
                    CREATE_ICMP_MONITOR,
                    mapOf(
                        "name" to "readonly-test-icmp-monitor",
                        "host" to "10.0.0.1",
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

            `when`("toggle-icmp-monitor is called") {
                val response = callTool(
                    TOGGLE_ICMP_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("delete-icmp-monitor is called") {
                val response = callTool(DELETE_ICMP_MONITOR, mapOf("monitorId" to 1L))

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
class IcmpMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-icmp-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-icmp-monitor is called for such a monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_ICMP_MONITOR, mapOf("monitorId" to monitor.id))

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
