package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.node.JsonNodeFactory

@MicronautTest
@Property(name = "app-config.incident-notes-enabled", value = "false")
class IncidentNoteMutabilityTest(
    @param:Client("/") private val client: HttpClient,
    private val incidentClient: IncidentClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val incidentNoteRepository: IncidentNoteRepository,
) : DatabaseStringSpec() {
    init {
        "all the API endpoints that mutate incident notes return a 405 if incident notes are disabled" {
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository).id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )
            val note = createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Original")
            val createDto = IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = "New")
            val updateDto = JsonNodeFactory.instance.objectNode().put("content", "Changed")

            table(
                headers("url", "method", "testBody"),
                row("/api/v2/incidents/HTTP/${event.id}/notes", HttpMethod.POST, createDto),
                row("/api/v2/incidents/notes/${note.id}", HttpMethod.PATCH, updateDto),
                row("/api/v2/incidents/notes/${note.id}", HttpMethod.DELETE, null),
            ).forAll { url, method, testBody ->
                val request = HttpRequest.create<Any>(method, url).apply { testBody?.let { body(it) } }
                val ex = shouldThrow<HttpClientResponseException> { client.exchange(request).awaitFirst() }

                ex.response.status shouldBe HttpStatus.METHOD_NOT_ALLOWED
                ex.message shouldContain "Incident notes are disabled"
            }

            // Nothing has been changed
            incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id).single().let { existing ->
                existing.id shouldBe note.id
                existing.content shouldBe "Original"
            }
        }

        "the existing notes are still readable if incident notes are disabled" {
            val event = createHttpUptimeEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository).id,
                status = UptimeStatus.DOWN,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )
            createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Readable")

            incidentClient.getIncidentDetails(IncidentType.HTTP, event.id)
                .shouldNotBeNull()
                .notes.single().content shouldBe "Readable"
        }
    }
}
