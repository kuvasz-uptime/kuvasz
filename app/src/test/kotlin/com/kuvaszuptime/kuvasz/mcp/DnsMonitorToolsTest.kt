package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_DNS_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_DNS_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_DNS_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_DNS_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_DNS_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_DNS_MONITOR
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mocks.createDnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
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
class DnsMonitorToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val dnsMonitorRepository: DnsMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-dns-monitors tool") {

            `when`("list-dns-monitors is called with monitors in the DB") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val response = callToolWithMcpClient(LIST_DNS_MONITORS)

                then("it should return the list in both structured and text content") {
                    response.isError shouldBe false

                    val monitorList = response.structuredContentAs<DnsMonitorListSchema>().shouldNotBeNull()
                    monitorList.monitors.forOne { it.name shouldBe monitor.name }

                    response.contentAs<DnsMonitorListSchema>() shouldBe monitorList
                }
            }
        }

        given("the get-dns-monitor-details tool") {

            `when`("get-dns-monitor-details is called with a valid ID") {
                val monitor = createDnsMonitor(
                    dnsMonitorRepository,
                    host = "example.com",
                    transport = DnsTransport.TCP,
                    recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")),
                )
                val response = callToolWithMcpClient(GET_DNS_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should return the details, including the DNS-specific fields") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<DnsMonitorDetailsSchema>().shouldNotBeNull()
                    details.id shouldBe monitor.id
                    details.name shouldBe monitor.name
                    details.host shouldBe "example.com"
                    details.transport shouldBe DnsTransport.TCP
                    details.recordMatchers.forOne { it.value shouldBe "1.2.3.4" }

                    response.contentAs<DnsMonitorDetailsSchema>() shouldBe details
                }
            }

            `when`("get-dns-monitor-details is called for a monitor under an active maintenance window") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "dns-maintenance",
                    enabled = true,
                    monitors = listOf(MonitorID(MonitorType.DNS, monitor.name)),
                )
                val response = callToolWithMcpClient(GET_DNS_MONITOR_DETAILS, mapOf("monitorId" to monitor.id))

                then("it should expose inMaintenance=true and the affecting maintenance window") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<DnsMonitorDetailsSchema>().shouldNotBeNull()
                    details.inMaintenance shouldBe true
                    details.maintenanceWindows.forOne { expectedWindow ->
                        expectedWindow.id shouldBe window.id
                        expectedWindow.name shouldBe window.name
                        expectedWindow.active shouldBe true
                    }
                }
            }

            `when`("get-dns-monitor-details is called with a non-existent ID") {
                val response = callTool(GET_DNS_MONITOR_DETAILS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-dns-monitor tool") {

            `when`("create-dns-monitor is called with a minimal, valid input") {
                val response = callToolWithMcpClient(
                    CREATE_DNS_MONITOR,
                    mapOf(
                        "name" to "mcp-created-dns-monitor",
                        "host" to "example.com",
                        "uptimeCheckInterval" to 60,
                    )
                )

                then("it should return the created monitor in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorSchema>().shouldNotBeNull()) {
                        name shouldBe "mcp-created-dns-monitor"
                        host shouldBe "example.com"
                        uptimeCheckInterval shouldBe 60
                        transport shouldBe DnsTransport.UDP
                        enabled shouldBe true

                        response.contentAs<DnsMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("create-dns-monitor is called with the DNS-specific options set") {
                val response = callToolWithMcpClient(
                    CREATE_DNS_MONITOR,
                    mapOf(
                        "name" to "mcp-created-dns-monitor-full",
                        "host" to "example.com",
                        "uptimeCheckInterval" to 60,
                        "transport" to "TCP",
                        "recordMatchers" to listOf(
                            mapOf("recordType" to "A", "matchType" to "EXACT", "value" to "1.2.3.4"),
                        ),
                        "driftDetectionEnabled" to true,
                        "driftRecordTypes" to listOf("NS", "MX"),
                    )
                )

                then("it should persist the DNS-specific fields") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorSchema>().shouldNotBeNull()) {
                        transport shouldBe DnsTransport.TCP
                        recordMatchers.forOne { it.value shouldBe "1.2.3.4" }
                        driftDetectionEnabled shouldBe true
                        driftRecordTypes shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
                    }
                }
            }

            `when`("create-dns-monitor is called with an invalid resolver port") {
                val response = callToolWithMcpClient(
                    CREATE_DNS_MONITOR,
                    mapOf(
                        "name" to "mcp-created-dns-monitor",
                        "host" to "example.com",
                        "uptimeCheckInterval" to 60,
                        "resolverPort" to 70000,
                    )
                )

                then("it should return an input schema validation error") {
                    response.shouldHaveInputValidationError("/resolverPort: must have a maximum value of 65535")
                }
            }

            `when`("create-dns-monitor is called with a duplicate name") {
                val existing = createDnsMonitor(dnsMonitorRepository, monitorName = "duplicate-dns-monitor")
                val response = callTool(
                    CREATE_DNS_MONITOR,
                    mapOf(
                        "name" to existing.name,
                        "host" to "example.org",
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

        given("the get-dns-monitor-stats tool") {

            `when`("get-dns-monitor-stats is called with a valid monitor ID") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val response = callToolWithMcpClient(
                    GET_DNS_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "PT12H"),
                )

                then("it should return stats in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorStatsSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        metricsHistoryEnabled shouldBe monitor.metricsHistoryEnabled

                        response.contentAs<DnsMonitorStatsSchema>() shouldBe this
                    }
                }
            }

            `when`("get-dns-monitor-stats is called with an invalid period string") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val response = callTool(
                    GET_DNS_MONITOR_STATS,
                    mapOf("monitorId" to monitor.id, "period" to "not-a-valid-period"),
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "Text cannot be parsed to a Duration",
                    )
                }
            }

            `when`("get-dns-monitor-stats is called with a non-existent monitor ID") {
                val response = callTool(GET_DNS_MONITOR_STATS, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }

            `when`("get-dns-monitor-stats is called for a monitor with metrics history") {
                val monitor = createDnsMonitor(dnsMonitorRepository, metricsHistoryEnabled = true)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 10)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = 20)
                createDnsMetricsLogRecord(dslContext, monitorId = monitor.id, latencyMs = null)

                val response = callToolWithMcpClient(GET_DNS_MONITOR_STATS, mapOf("monitorId" to monitor.id))

                then("it should return populated LatencyStatsSchema and DnsMetricsLogSchema entries") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorStatsSchema>().shouldNotBeNull()) {
                        latencyStats.shouldNotBeNull()
                        metricsLogs.shouldNotBeEmpty()

                        response.contentAs<DnsMonitorStatsSchema>() shouldBe this
                    }
                }
            }
        }

        given("the toggle-dns-monitor tool") {

            `when`("toggle-dns-monitor is called to disable a monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository, enabled = true)
                val response = callToolWithMcpClient(
                    TOGGLE_DNS_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to false)
                )

                then("it should return the updated monitor with enabled=false in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe false

                        response.contentAs<DnsMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-dns-monitor is called to enable a monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository, enabled = false)
                val response = callToolWithMcpClient(
                    TOGGLE_DNS_MONITOR,
                    mapOf("monitorId" to monitor.id, "enabled" to true)
                )

                then("it should return the updated monitor with enabled=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DnsMonitorSchema>().shouldNotBeNull()) {
                        id shouldBe monitor.id
                        enabled shouldBe true

                        response.contentAs<DnsMonitorSchema>() shouldBe this
                    }
                }
            }

            `when`("toggle-dns-monitor is called with a non-existent monitor ID") {
                val response = callTool(TOGGLE_DNS_MONITOR, mapOf("monitorId" to -999L, "enabled" to false))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the delete-dns-monitor tool") {

            `when`("delete-dns-monitor is called with a valid monitor ID") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                val response = callToolWithMcpClient(DELETE_DNS_MONITOR, mapOf("monitorId" to monitor.id))

                then("it should return a delete result with deleted=true in both structured and text content") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull()) {
                        deleted shouldBe true
                        id shouldBe monitor.id

                        response.contentAs<DeleteResultSchema>() shouldBe this
                    }
                }
            }

            `when`("delete-dns-monitor is called with a non-existent monitor ID") {
                val response = callTool(DELETE_DNS_MONITOR, mapOf("monitorId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest(environments = ["yaml-dns-monitors-empty-array"])
class DnsReadOnlyMonitorMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the DNS monitor MCP tools when monitors are configured via YAML") {

            `when`("create-dns-monitor is called") {
                val response = callTool(
                    CREATE_DNS_MONITOR,
                    mapOf(
                        "name" to "readonly-test-dns-monitor",
                        "host" to "example.com",
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

            `when`("toggle-dns-monitor is called") {
                val response = callTool(
                    TOGGLE_DNS_MONITOR,
                    mapOf("monitorId" to 1L, "enabled" to false)
                )

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(
                        McpSchema.ErrorCodes.INVALID_REQUEST,
                        "The given type of monitors were configured via a YAML file",
                    )
                }
            }

            `when`("delete-dns-monitor is called") {
                val response = callTool(DELETE_DNS_MONITOR, mapOf("monitorId" to 1L))

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
class DnsMonitorReferencedByStatusPageMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val appConfig: AppConfig,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the delete-dns-monitor tool when the monitor is referenced by a read-only status page") {

            `when`("delete-dns-monitor is called for such a monitor") {
                val monitor = createDnsMonitor(dnsMonitorRepository)
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.DNS, monitor.name)))
                appConfig.disableStatusPageExternalWrite()

                val response = callTool(DELETE_DNS_MONITOR, mapOf("monitorId" to monitor.id))

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
