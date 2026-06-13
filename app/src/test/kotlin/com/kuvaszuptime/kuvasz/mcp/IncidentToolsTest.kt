package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INCIDENTS
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentListSchema
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest
class IncidentToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-incidents tool") {

            `when`("list-incidents is called with an ongoing incident in the DB") {
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                    error = "Connection refused",
                )
                val response = callToolWithMcpClient(LIST_INCIDENTS)

                then("it should return the incident in both structured and text content") {
                    response.isError shouldBe false

                    val incidentList = response.structuredContentAs<IncidentListSchema>().shouldNotBeNull()
                    incidentList.incidents.forOne { it.monitorName shouldBe monitor.name }

                    response.contentAs<IncidentListSchema>() shouldBe incidentList
                }
            }

            `when`("list-incidents is called with an ISO 8601 period string") {
                val monitor = createHttpMonitor(monitorRepository)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusHours(1),
                    endedAt = null,
                )
                val response = callToolWithMcpClient(LIST_INCIDENTS, mapOf("period" to "PT2H"))

                then("it should resolve the duration and return matching incidents") {
                    response.isError shouldBe false

                    val incidentList = response.structuredContentAs<IncidentListSchema>().shouldNotBeNull()
                    incidentList.incidents.forOne { it.monitorName shouldBe monitor.name }

                    response.contentAs<IncidentListSchema>() shouldBe incidentList
                }
            }

            `when`("list-incidents is called with an invalid period string") {
                val response = callTool(LIST_INCIDENTS, mapOf("period" to "not-a-valid-period"))

                then("it should return an invalid-request protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_REQUEST)
                }
            }

            `when`("list-incidents is filtered by monitorId with no incidents") {
                val monitor = createHttpMonitor(monitorRepository)
                val response = callToolWithMcpClient(
                    LIST_INCIDENTS,
                    mapOf("monitorId" to monitor.id, "includeResolved" to false),
                )

                then("it should return an empty incidents list in both structured and text content") {
                    response.isError shouldBe false

                    val incidentList = response.structuredContentAs<IncidentListSchema>().shouldNotBeNull()
                    incidentList.incidents.shouldBeEmpty()

                    response.contentAs<IncidentListSchema>() shouldBe incidentList
                }
            }
        }
    }
}
