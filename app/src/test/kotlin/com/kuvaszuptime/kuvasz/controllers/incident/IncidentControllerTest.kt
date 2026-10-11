package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.IncidentNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentNoteNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.services.incident.IncidentActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Duration
import java.time.ZoneOffset

@MicronautTest
class IncidentControllerTest(
    incidentClient: IncidentClient,
    incidentRepository: IncidentRepository,
    incidentActions: IncidentActions,
    @Client("/") rawClient: HttpClient,
) : DatabaseBehaviorSpec({

    val now = getCurrentTimestamp().withOffsetSameInstant(ZoneOffset.UTC)
    val incident = IncidentDto(
        monitorId = 1L,
        monitorName = "Monitor",
        isMonitorEnabled = true,
        incidentType = IncidentType.HTTP,
        eventId = 42L,
        status = IncidentStatus.RESOLVED,
        details = "Connection refused",
        startedAt = now.minusHours(1),
        endedAt = now,
        updatedAt = now,
    )
    val note = IncidentNoteDto(
        id = 7L,
        incidentType = IncidentType.HTTP,
        eventId = 42L,
        status = InvestigationStatus.INVESTIGATING,
        content = "Looking into it",
        timestamp = null,
        public = true,
        createdAt = now,
        updatedAt = now,
    )

    suspend fun statusOf(request: MutableHttpRequest<*>): HttpStatus =
        try {
            rawClient.exchange(request).awaitFirst().status
        } catch (ex: HttpClientResponseException) {
            ex.status
        }

    given("the getIncidents() endpoint") {

        `when`("it is called without explicit parameters") {
            val monitorId = 123L

            then("it should delegate to the IncidentRepository with default parameters") {
                val mockRepo = getMock(incidentRepository)
                every {
                    mockRepo.getIncidents(any(), any(), any())
                } returns emptyList()

                val result = incidentClient.getIncidents(monitorId, null, null)

                result.shouldBeEmpty()
                verify(exactly = 1) { mockRepo.getIncidents(monitorId, Duration.ofDays(7), true) }
            }
        }

        `when`("it is called with explicit parameters") {
            val monitorId = 123L

            then("it should delegate to the IncidentRepository with the provided parameters") {
                val mockRepo = getMock(incidentRepository)
                every {
                    mockRepo.getIncidents(any(), any(), any())
                } returns emptyList()

                val result = incidentClient.getIncidents(monitorId, Duration.ofMinutes(10), false)

                result.shouldBeEmpty()
                verify(exactly = 1) { mockRepo.getIncidents(monitorId, Duration.ofMinutes(10), false) }
            }
        }
    }

    given("the getIncidentDetails() endpoint") {

        `when`("the incident exists") {

            then("it should return its details from IncidentActions") {
                val details = IncidentDetailsDto(
                    incident = incident,
                    notes = listOf(note),
                    integrations = listOf(IntegrationID(IntegrationType.SLACK, "ops")),
                    resolutionIntegrations = emptyList(),
                )
                every { getMock(incidentActions).getIncidentDetails(IncidentType.HTTP, 42L) } returns details

                incidentClient.getIncidentDetails(IncidentType.HTTP, 42L) shouldBe details
            }
        }

        `when`("the incident doesn't exist") {

            then("it should return a 404") {
                every {
                    getMock(incidentActions).getIncidentDetails(IncidentType.SSL, 42L)
                } throws IncidentNotFoundException(IncidentType.SSL, 42L)

                statusOf(HttpRequest.GET<Any>("/api/v2/incidents/SSL/42")) shouldBe HttpStatus.NOT_FOUND
            }
        }

        `when`("the incident type is unknown") {

            then("it should return a 400") {
                statusOf(HttpRequest.GET<Any>("/api/v2/incidents/FOO/42")) shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    given("the createIncidentNote() endpoint") {

        `when`("the request is valid") {

            then("it should create the note via IncidentActions, and return a 201") {
                val createDto = IncidentNoteCreateDto(
                    status = InvestigationStatus.INVESTIGATING,
                    content = "Looking into it",
                    public = true,
                )
                every { getMock(incidentActions).createNote(IncidentType.HTTP, 42L, createDto) } returns note

                incidentClient.createIncidentNote(IncidentType.HTTP, 42L, createDto) shouldBe note
                statusOf(HttpRequest.POST("/api/v2/incidents/HTTP/42/notes", createDto)) shouldBe HttpStatus.CREATED
            }
        }

        `when`("the content is blank") {

            then("it should return a 400 without trying to create the note") {
                shouldThrow<HttpClientResponseException> {
                    incidentClient.createIncidentNote(
                        IncidentType.HTTP,
                        42L,
                        IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = " "),
                    )
                }.status shouldBe HttpStatus.BAD_REQUEST
                verify(exactly = 0) { getMock(incidentActions).createNote(any(), any(), any()) }
            }
        }

        `when`("the status is missing") {

            then("it should return a 400") {
                statusOf(
                    HttpRequest.POST("/api/v2/incidents/HTTP/42/notes", mapOf("content" to "Looking into it"))
                ) shouldBe HttpStatus.BAD_REQUEST
            }
        }

        `when`("the incident doesn't exist") {

            then("it should return a 404") {
                every {
                    getMock(incidentActions).createNote(IncidentType.TCP, 42L, any())
                } throws IncidentNotFoundException(IncidentType.TCP, 42L)

                statusOf(
                    HttpRequest.POST(
                        "/api/v2/incidents/TCP/42/notes",
                        IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = "Checking"),
                    )
                ) shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("the updateIncidentNote() endpoint") {

        `when`("the note exists") {

            then("it should pass the updates to IncidentActions") {
                val updates = JsonNodeFactory.instance.objectNode().put("content", "Changed").putNull("timestamp")
                every { getMock(incidentActions).updateNote(7L, updates) } returns note.copy(content = "Changed")

                incidentClient.updateIncidentNote(7L, updates).content shouldBe "Changed"
            }
        }

        `when`("the note doesn't exist") {

            then("it should return a 404") {
                every { getMock(incidentActions).updateNote(8L, any()) } throws IncidentNoteNotFoundException(8L)

                statusOf(
                    HttpRequest.PATCH("/api/v2/incidents/notes/8", JsonNodeFactory.instance.objectNode())
                ) shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    given("the deleteIncidentNote() endpoint") {

        `when`("the note exists") {

            then("it should delete it via IncidentActions, and return a 204") {
                every { getMock(incidentActions).deleteNote(7L) } returns Unit

                statusOf(HttpRequest.DELETE<Any>("/api/v2/incidents/notes/7")) shouldBe HttpStatus.NO_CONTENT
                verify(exactly = 1) { getMock(incidentActions).deleteNote(7L) }
            }
        }

        `when`("the note doesn't exist") {

            then("it should return a 404") {
                every { getMock(incidentActions).deleteNote(8L) } throws IncidentNoteNotFoundException(8L)

                statusOf(HttpRequest.DELETE<Any>("/api/v2/incidents/notes/8")) shouldBe HttpStatus.NOT_FOUND
            }
        }
    }
}) {
    @MockBean(IncidentRepository::class)
    fun mockIncidentRepository(): IncidentRepository = mockk()

    @MockBean(IncidentActions::class)
    fun mockIncidentActions(): IncidentActions = mockk()
}
