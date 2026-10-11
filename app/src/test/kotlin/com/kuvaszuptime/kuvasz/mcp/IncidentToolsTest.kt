package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_INCIDENT_NOTE
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_INCIDENT_NOTE
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_INCIDENT_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INCIDENTS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentListSchema
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest
class IncidentToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    private val incidentNoteRepository: IncidentNoteRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        fun downEvent() = createHttpUptimeEventRecord(
            dslContext,
            monitorId = createHttpMonitor(monitorRepository).id,
            status = UptimeStatus.DOWN,
            startedAt = getCurrentTimestamp().minusHours(1),
            endedAt = null,
            error = "Connection refused",
            integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
        )

        given("the list-incidents tool") {

            `when`("list-incidents is called with an ongoing incident in the DB") {
                val monitor = createHttpMonitor(monitorRepository)
                val event = createHttpUptimeEventRecord(
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
                    incidentList.incidents.forOne { incident ->
                        incident.monitorName shouldBe monitor.name
                        incident.eventId shouldBe event.id
                    }

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

        given("the get-incident-details tool") {

            `when`("it is called for an existing incident with a note") {
                val event = downEvent()
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "On it")
                val response = callToolWithMcpClient(
                    GET_INCIDENT_DETAILS,
                    mapOf("incidentType" to "http", "eventId" to event.id),
                )

                then("it should return the incident with its notes and notified integrations") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IncidentDetailsSchema>().shouldNotBeNull()) {
                        incident.eventId shouldBe event.id
                        notes.single().content shouldBe "On it"
                        integrations shouldBe listOf("slack:ops")
                        resolutionIntegrations.shouldBeNull()

                        response.contentAs<IncidentDetailsSchema>() shouldBe this
                    }
                }
            }

            `when`("it is called for a resolved SSL incident") {
                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(monitorRepository)
                val event = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = SslStatus.INVALID,
                    startedAt = now.minusHours(1),
                    endedAt = now,
                    integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = SslStatus.VALID,
                    startedAt = now,
                    endedAt = null,
                    integrations = arrayOf(IntegrationID(IntegrationType.PAGERDUTY, "on-call")),
                )
                val response = callToolWithMcpClient(
                    GET_INCIDENT_DETAILS,
                    mapOf("incidentType" to "SSL", "eventId" to event.id),
                )

                then("it should return the integrations notified about the resolution as well") {
                    response.isError shouldBe false

                    with(response.structuredContentAs<IncidentDetailsSchema>().shouldNotBeNull()) {
                        incident.incidentType shouldBe IncidentType.SSL
                        integrations shouldBe listOf("slack:ops")
                        resolutionIntegrations shouldBe listOf("pagerduty:on-call")
                    }
                }
            }

            `when`("it is called with an unknown incident type") {
                val response = callTool(GET_INCIDENT_DETAILS, mapOf("incidentType" to "FOO", "eventId" to 1L))

                then("it should return an invalid-params protocol error, listing every valid type") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_PARAMS)
                    IncidentType.entries.forEach { type ->
                        response.error.shouldNotBeNull().message shouldContain "'${type.name}'"
                    }
                }
            }

            `when`("it is called for a non-existent incident") {
                val response = callTool(GET_INCIDENT_DETAILS, mapOf("incidentType" to "HTTP", "eventId" to -999L))

                then("it should return a resource-not-found protocol error") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }

        given("the create-incident-note tool") {

            `when`("it is called with a valid input") {
                val event = downEvent()
                val timestamp = getCurrentTimestamp().minusMinutes(10)
                val response = callToolWithMcpClient(
                    CREATE_INCIDENT_NOTE,
                    mapOf(
                        "incidentType" to "HTTP",
                        "eventId" to event.id,
                        "status" to "IDENTIFIED",
                        "content" to "The database is down",
                        "timestamp" to timestamp.toString(),
                        "public" to true,
                    ),
                )

                then("it should create the note, and return the details of the incident") {
                    response.isError shouldBe false

                    response.structuredContentAs<IncidentDetailsSchema>().shouldNotBeNull()
                        .notes.single().let { note ->
                            note.status shouldBe InvestigationStatus.IDENTIFIED
                            note.content shouldBe "The database is down"
                            note.timestamp shouldBe timestamp
                            note.public shouldBe true
                        }
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id).single().content shouldBe
                        "The database is down"
                }
            }

            `when`("it is called with a blank content") {
                val event = downEvent()
                val response = callTool(
                    CREATE_INCIDENT_NOTE,
                    mapOf(
                        "incidentType" to "HTTP",
                        "eventId" to event.id,
                        "status" to "INVESTIGATING",
                        "content" to " ",
                    ),
                )

                then("it should return an invalid-params protocol error, and create nothing") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_PARAMS)
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id).shouldBeEmpty()
                }
            }
        }

        given("the delete-incident-note tool") {

            `when`("it is called for an existing note") {
                val note = createIncidentNote(dslContext, IncidentType.HTTP, downEvent().id)
                val response = callToolWithMcpClient(DELETE_INCIDENT_NOTE, mapOf("incidentNoteId" to note.id))

                then("it should delete the note") {
                    response.isError shouldBe false

                    response.structuredContentAs<DeleteResultSchema>().shouldNotBeNull() shouldBe
                        DeleteResultSchema(deleted = true, id = note.id)
                    incidentNoteRepository.findById(note.id).shouldBeNull()
                }
            }

            `when`("it is called for a non-existent note") {
                val response = callTool(DELETE_INCIDENT_NOTE, mapOf("incidentNoteId" to -999L))

                then("it should return a resource-not-found protocol error") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}

@MicronautTest
@Property(name = "app-config.incident-notes-enabled", value = "false")
class DisabledIncidentNoteMcpToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    private val incidentNoteRepository: IncidentNoteRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the incident note MCP tools when incident notes are disabled") {

            `when`("create-incident-note is called") {
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(monitorRepository).id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                val response = callTool(
                    CREATE_INCIDENT_NOTE,
                    mapOf(
                        "incidentType" to "HTTP",
                        "eventId" to event.id,
                        "status" to "INVESTIGATING",
                        "content" to "Nasty stuff",
                    ),
                )

                then("it should return an invalid-request protocol error, and create nothing") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_REQUEST, "Incident notes are disabled")
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id).shouldBeEmpty()
                }
            }

            `when`("delete-incident-note is called") {
                val note = createIncidentNote(dslContext, IncidentType.HTTP, eventId = 1L)
                val response = callTool(DELETE_INCIDENT_NOTE, mapOf("incidentNoteId" to note.id))

                then("it should return an invalid-request protocol error, and keep the note") {
                    response.shouldHaveError(McpSchema.ErrorCodes.INVALID_REQUEST, "Incident notes are disabled")
                    incidentNoteRepository.findById(note.id).shouldNotBeNull()
                }
            }

            `when`("get-incident-details is called") {
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(monitorRepository).id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Readable")
                val response = callToolWithMcpClient(
                    GET_INCIDENT_DETAILS,
                    mapOf("incidentType" to "HTTP", "eventId" to event.id),
                )

                then("it should still return the existing notes") {
                    response.isError shouldBe false
                    response.structuredContentAs<IncidentDetailsSchema>().shouldNotBeNull()
                        .notes.single().content shouldBe "Readable"
                }
            }
        }
    }
}
