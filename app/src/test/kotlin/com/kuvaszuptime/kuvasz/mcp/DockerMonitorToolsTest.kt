package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_DOCKER_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_DOCKER_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_DOCKER_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createDockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import com.kuvaszuptime.kuvasz.testutils.shouldHaveInputValidationError
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest(environments = ["full-integrations-setup", "docker-hosts"])
class DockerMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val dockerMonitorRepository: DockerMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-docker-monitors tool") {

            `when`("list-docker-monitors is called with monitors in the DB") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val response = callToolWithMcpClient(LIST_DOCKER_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<DockerMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<DockerMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-docker-monitor-details tool") {

            `when`("get-docker-monitor-details is called with a valid ID") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val response = callToolWithMcpClient(GET_DOCKER_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<DockerMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name
                    details.ignoreConnectivityCheck shouldBe monitor.ignoreConnectivityCheck
                    details.dockerHost shouldBe monitor.dockerHost
                    details.container shouldBe monitor.container

                    response.contentAs<DockerMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-docker-monitor-details is called for a monitor under an active maintenance window") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "docker-maintenance",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name)),
                )
                val response = callToolWithMcpClient(GET_DOCKER_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should expose inMaintenance=true and the affecting maintenance window") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<DockerMonitorDetailsSchema>().shouldNotBeNull()
                    details.inMaintenance shouldBe true
                    details.maintenanceWindows.forOne { expectedWindow ->
                        expectedWindow.id shouldBe window.id
                        expectedWindow.name shouldBe window.name
                        expectedWindow.active shouldBe true
                    }
                }
            }

            `when`("get-docker-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_DOCKER_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-docker-monitor tool") {

            `when`("create-docker-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to "mcp-created-docker-monitor",
                        "dockerHost" to "local",
                        "container" to "my-app",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DockerMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-docker-monitor"
                        dockerHost shouldBe "local"
                        container shouldBe "my-app"
                        uptimeCheckInterval shouldBe 60
                        enabled shouldBe true
                        ignoreConnectivityCheck shouldBe DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK

                        response.contentAs<DockerMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-docker-monitor is called with ignoreConnectivityCheck set") {
                val response = callToolWithMcpClient(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to "mcp-created-docker-monitor-ignoring-connectivity",
                        "dockerHost" to "local",
                        "container" to "my-app",
                        "uptimeCheckInterval" to 60,
                        "ignoreConnectivityCheck" to true,
                    )
                )

                then("the created monitor should keep being checked without outbound connectivity") {
                    response.isError shouldBe false

                    response.structuredContentAs<DockerMonitorSchema>().shouldNotBeNull()
                        .ignoreConnectivityCheck shouldBe true
                    dockerMonitorRepository.findByName("mcp-created-docker-monitor-ignoring-connectivity")
                        .shouldNotBeNull().ignoreConnectivityCheck shouldBe true
                }
            }

            `when`("create-docker-monitor is called with an out-of-range timeout") {
                val response = callToolWithMcpClient(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to "mcp-created-docker-monitor",
                        "dockerHost" to "local",
                        "container" to "my-app",
                        "uptimeCheckInterval" to 60,
                        "timeoutMs" to 30001,
                    )
                )

                then("it should return an input schema validation error") {
                    response.shouldHaveInputValidationError("/timeoutMs: must have a maximum value of 30000")
                }
            }

            `when`("create-docker-monitor is called with a Docker host that is not configured") {
                val response = callTool(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to "mcp-docker-monitor-on-a-missing-host",
                        "dockerHost" to "not-configured",
                        "container" to "my-app",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return an invalid-params protocol error and not create the monitor") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "Non-existing Docker host found: not-configured.",
                    )
                    dockerMonitorRepository.findByName("mcp-docker-monitor-on-a-missing-host").shouldBeNull()
                }
            }

            `when`("create-docker-monitor is called with a duplicate name") {
                val existing = createDockerMonitor(dockerMonitorRepository, monitorName = "duplicate-docker-monitor")
                val response = callTool(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "dockerHost" to "local",
                        "container" to "my-app",
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

        given("the get-docker-monitor-stats tool") {

            `when`("get-docker-monitor-stats is called with a valid monitor ID") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_DOCKER_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DockerMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                        response.contentAs<DockerMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-docker-monitor-stats is called with an invalid period string") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val response = callTool(
                    GET_DOCKER_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration",
                    )
                }
            }

            `when`("get-docker-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_DOCKER_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }

            `when`("get-docker-monitor-stats is called for a monitor with metrics history") {
                val monitor = createDockerMonitor(dockerMonitorRepository, metricsHistoryEnabled = true)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 10)
                createDockerMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 20)

                val response = callToolWithMcpClient(GET_DOCKER_MONITOR_STATS, mapOf("monitorId" to monitor.id))

                then("it should return populated resource stats and DockerMetricsLogSchema entries") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DockerMonitorStatsSchema>().shouldNotBeNull()) {
                        cpuStats.shouldNotBeNull()
                        memoryStats.shouldNotBeNull()
                        metricsLogs.shouldNotBeEmpty()

                        response.contentAs<DockerMonitorStatsSchema>() shouldBe this
                    }
                }
            }
        }

        given("the toggle-docker-monitor tool") {

            `when`("toggle-docker-monitor is called to disable a monitor") {
                val monitor = createDockerMonitor(dockerMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_DOCKER_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DockerMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<DockerMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-docker-monitor is called to enable a monitor") {
                val monitor = createDockerMonitor(dockerMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_DOCKER_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DockerMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<DockerMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-docker-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_DOCKER_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-docker-monitor tool") {

            `when`("delete-docker-monitor is called with a valid monitor ID") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                val response = callToolWithMcpClient(DELETE_DOCKER_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-docker-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_DOCKER_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-docker-monitors-empty-array"])
class DockerReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the DOCKER monitor MCP tools when monitors are configured via YAML") {

            `when`("create-docker-monitor is called") {
                val response = callTool(
                    CREATE_DOCKER_MONITOR,
                    mapOf(
                        "name" to "readonly-test-docker-monitor",
                        "dockerHost" to "local",
                        "container" to "my-app",
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

            `when`("toggle-docker-monitor is called") {
                val response = callTool(
                    TOGGLE_DOCKER_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("delete-docker-monitor is called") {
                val response = callTool(DELETE_DOCKER_MONITOR, mapOf("monitorId" to 1L))

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
class DockerMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val dockerMonitorRepository: DockerMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-docker-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-docker-monitor is called for such a monitor") {
                val monitor = createDockerMonitor(dockerMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.DOCKER, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_DOCKER_MONITOR, mapOf("monitorId" to monitor.id))

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
