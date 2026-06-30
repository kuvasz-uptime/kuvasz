package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_MAINTENANCE_WINDOW_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_MAINTENANCE_WINDOWS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowSchema
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest
class MaintenanceWindowToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-maintenance-windows tool") {

            `when`("list-maintenance-windows is called with no windows in the DB") {
                val response = callToolWithMcpClient(LIST_MAINTENANCE_WINDOWS)

                then("it should return an empty list in both structured and text content") {
                    response.isError shouldBe false

                    val list = response.structuredContentAs<MaintenanceWindowListSchema>().shouldNotBeNull()
                    list.maintenanceWindows.shouldBeEmpty()

                    response.contentAs<MaintenanceWindowListSchema>() shouldBe list
                }
            }

            `when`("list-maintenance-windows is called with a manual, enabled window in the DB") {
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "manual-window",
                    enabled = true,
                    global = true,
                )
                val response = callToolWithMcpClient(LIST_MAINTENANCE_WINDOWS)

                then("it should populate the summary fields correctly and mark it active") {
                    response.isError shouldBe false

                    val list = response.structuredContentAs<MaintenanceWindowListSchema>().shouldNotBeNull()
                    list.maintenanceWindows shouldHaveSize 1
                    with(list.maintenanceWindows.first()) {
                        id shouldBe window.id
                        name shouldBe "manual-window"
                        enabled shouldBe true
                        global shouldBe true
                        active shouldBe true
                    }

                    response.contentAs<MaintenanceWindowListSchema>() shouldBe list
                }
            }
        }

        given("the get-maintenance-window-details tool") {

            `when`("get-maintenance-window-details is called with a valid ID") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "details-window",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
                )
                val response = callToolWithMcpClient(
                    GET_MAINTENANCE_WINDOW_DETAILS,
                    mapOf("maintenanceWindowId" to window.id),
                )

                then("it should return the full details in both structured and text content") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<MaintenanceWindowSchema>().shouldNotBeNull()
                    details.id shouldBe window.id
                    details.name shouldBe "details-window"
                    details.monitors shouldContain "http:${monitor.name}"
                    details.active shouldBe true

                    response.contentAs<MaintenanceWindowSchema>() shouldBe details
                }
            }

            `when`("get-maintenance-window-details is called with a non-existent ID") {
                val response = callTool(GET_MAINTENANCE_WINDOW_DETAILS, mapOf("maintenanceWindowId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-maintenance-window tool") {

            `when`("create-maintenance-window is called with a minimal, valid manual window") {
                val response = callToolWithMcpClient(
                    CREATE_MAINTENANCE_WINDOW,
                    mapOf("name" to "mcp-created-window"),
                )

                then("it should return the created window in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<MaintenanceWindowSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-window"
                        enabled shouldBe true

                        response.contentAs<MaintenanceWindowSchema>() shouldBe this
                    }
                }
            }

            `when`("create-maintenance-window is called with a recurring window with a valid cron and duration") {
                val response = callToolWithMcpClient(
                    CREATE_MAINTENANCE_WINDOW,
                    mapOf(
                        "name" to "mcp-cron-window",
                        "cron" to "0 2 * * *",
                        "duration" to "PT1H",
                    ),
                )

                then("it should create the recurring window and compute the next start") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<MaintenanceWindowSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-cron-window"
                        cron shouldBe "0 2 * * *"
                        duration shouldBe "PT1H"
                        nextStart.shouldNotBeNull()
                    }
                }
            }

            `when`("create-maintenance-window is called with a blank name") {
                val response = callTool(CREATE_MAINTENANCE_WINDOW, mapOf("name" to ""))

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_PARAMS)
                }
            }

            `when`("create-maintenance-window is called with an invalid schedule (both cron and start)") {
                val response = callTool(
                    CREATE_MAINTENANCE_WINDOW,
                    mapOf(
                        "name" to "invalid-schedule",
                        "cron" to "0 2 * * *",
                        "start" to "2026-01-01T00:00:00Z",
                        "duration" to "PT1H",
                    ),
                )

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_PARAMS)
                }
            }

            `when`("create-maintenance-window is called with a duplicate name") {
                val existing = createMaintenanceWindow(dslContext, name = "duplicate-window")
                val response = callTool(CREATE_MAINTENANCE_WINDOW, mapOf("name" to existing.name))

                then("it should return an invalid-params protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_PARAMS,
                        "There is already a maintenance window with the given name",
                    )
                }
            }
        }

        given("the toggle-maintenance-window tool") {

            `when`("toggle-maintenance-window is called to disable a window") {
                val window = createMaintenanceWindow(dslContext, name = "to-disable", enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_MAINTENANCE_WINDOW,
                    mapOf("maintenanceWindowId" to window.id, "enabled" to false),
                )

                then("it should return the updated window with enabled=false") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<MaintenanceWindowSchema>().shouldNotBeNull()) {
                        id shouldBe window.id
                        enabled shouldBe false

                        response.contentAs<MaintenanceWindowSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-maintenance-window is called to enable a window") {
                val window = createMaintenanceWindow(dslContext, name = "to-enable", enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_MAINTENANCE_WINDOW,
                    mapOf("maintenanceWindowId" to window.id, "enabled" to true),
                )

                then("it should return the updated window with enabled=true") {
                    response.isError shouldBe false
                    response.structuredContentAs<MaintenanceWindowSchema>().shouldNotBeNull().enabled shouldBe true
                }
            }

            `when`("toggle-maintenance-window is called with a non-existent ID") {
                val response = callTool(
                    TOGGLE_MAINTENANCE_WINDOW,
                    mapOf("maintenanceWindowId" to -999L, "enabled" to false),
                )

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-maintenance-window tool") {

            `when`("delete-maintenance-window is called with a valid ID") {
                val window = createMaintenanceWindow(dslContext, name = "to-delete")
                val response = callToolWithMcpClient(
                    DELETE_MAINTENANCE_WINDOW,
                    mapOf("maintenanceWindowId" to window.id),
                )

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe window.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-maintenance-window is called with a non-existent ID") {
                val response = callTool(DELETE_MAINTENANCE_WINDOW, mapOf("maintenanceWindowId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest
class ReadOnlyMaintenanceWindowMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the maintenance window MCP write tools when windows are configured via YAML") {

            `when`("create-maintenance-window is called") {
                appConfig.disableMaintenanceWindowExternalWrite()
                val response = callTool(CREATE_MAINTENANCE_WINDOW, mapOf("name" to "readonly-window"))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The maintenance windows were configured via a YAML file",
                    )
                }
            }

            `when`("toggle-maintenance-window is called") {
                appConfig.disableMaintenanceWindowExternalWrite()
                val response = callTool(
                    TOGGLE_MAINTENANCE_WINDOW,
                    mapOf("maintenanceWindowId" to 1L, "enabled" to false),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The maintenance windows were configured via a YAML file",
                    )
                }
            }

            `when`("delete-maintenance-window is called") {
                appConfig.disableMaintenanceWindowExternalWrite()
                val response = callTool(DELETE_MAINTENANCE_WINDOW, mapOf("maintenanceWindowId" to 1L))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The maintenance windows were configured via a YAML file",
                    )
                }
            }
        }
    }
}
