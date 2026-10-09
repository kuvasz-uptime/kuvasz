package com.kuvaszuptime.kuvasz.services.incident

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentNoteNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.DEFAULT_PAGE_CACHE_NAME
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.STATUS_PAGES_CACHE_NAME
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.cache.CacheManager
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import jakarta.validation.ValidationException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class IncidentActionsTest(
    private val incidentActions: IncidentActions,
    private val incidentNoteRepository: IncidentNoteRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val cacheManager: CacheManager<*>,
) : DatabaseBehaviorSpec() {
    init {
        val mapper = jacksonObjectMapper()

        fun downEvent(endedAt: OffsetDateTime? = null) = createHttpUptimeEventRecord(
            dslContext,
            monitorId = createHttpMonitor(httpMonitorRepository).id,
            status = UptimeStatus.DOWN,
            startedAt = getCurrentTimestamp().minusHours(1),
            endedAt = endedAt,
            error = "Connection refused",
            integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
        )

        val statusPageCaches = listOf(DEFAULT_PAGE_CACHE_NAME, STATUS_PAGES_CACHE_NAME)

        fun fillStatusPageCaches() = statusPageCaches.forEach { cacheManager.getCache(it).put("cached", "data") }

        // Either both of them are evicted, or none of them
        fun statusPageCachesAreEvicted(): Boolean? =
            statusPageCaches
                .map { cacheManager.getCache(it).get("cached", String::class.java).isEmpty }
                .distinct()
                .singleOrNull()

        given("the getIncidentDetails() method") {

            `when`("the incident exists") {

                then("it should return the incident with its notes and notified integrations") {
                    val event = downEvent()
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "On it")

                    val details = incidentActions.getIncidentDetails(IncidentType.HTTP, event.id)

                    details.incident.eventId shouldBe event.id
                    details.incident.details shouldBe "Connection refused"
                    details.notes.single().id shouldBe note.id
                    details.notes.single().content shouldBe "On it"
                    details.integrations shouldBe listOf(IntegrationID(IntegrationType.SLACK, "ops"))
                    details.resolutionIntegrations.shouldBeNull()
                }
            }

            `when`("the incident doesn't exist") {

                then("it should throw an IncidentNotFoundException") {
                    shouldThrow<IncidentNotFoundException> {
                        incidentActions.getIncidentDetails(IncidentType.HTTP, 1234L)
                    }.message shouldContain "1234"
                }
            }
        }

        given("the createNote() method") {

            `when`("the incident exists") {

                then("it should create the note") {
                    val event = downEvent()
                    val timestamp = getCurrentTimestamp().minusMinutes(30)

                    val note = incidentActions.createNote(
                        IncidentType.HTTP,
                        event.id,
                        IncidentNoteCreateDto(
                            status = InvestigationStatus.IDENTIFIED,
                            content = "The database is down",
                            timestamp = timestamp,
                            public = true,
                        ),
                    )

                    note.incidentType shouldBe IncidentType.HTTP
                    note.eventId shouldBe event.id
                    note.status shouldBe InvestigationStatus.IDENTIFIED
                    note.content shouldBe "The database is down"
                    note.timestamp shouldBe timestamp
                    note.public shouldBe true
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id).single() shouldBe note
                }
            }

            `when`("the incident is an SSL incident") {

                then("it should create the note") {
                    val sslEvent = createSSLEventRecord(
                        dslContext,
                        monitorId = createHttpMonitor(httpMonitorRepository).id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )

                    incidentActions.createNote(
                        IncidentType.SSL,
                        sslEvent.id,
                        IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = "Checking"),
                    ).public shouldBe false
                    incidentNoteRepository.fetchByEvent(IncidentType.SSL, sslEvent.id).single().content shouldBe
                        "Checking"
                }
            }

            `when`("the event is not an incident") {

                then("it should throw an IncidentNotFoundException, and create nothing") {
                    val upEvent = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = createHttpMonitor(httpMonitorRepository).id,
                        status = UptimeStatus.UP,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )

                    shouldThrow<IncidentNotFoundException> {
                        incidentActions.createNote(
                            IncidentType.HTTP,
                            upEvent.id,
                            IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = "Checking"),
                        )
                    }
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, upEvent.id).shouldBeEmpty()
                }
            }
        }

        given("the updateNote() method") {

            `when`("only some of the fields are present") {

                then("only those should be updated") {
                    val event = downEvent()
                    val note = createIncidentNote(
                        dslContext,
                        IncidentType.HTTP,
                        event.id,
                        content = "Original",
                        timestamp = getCurrentTimestamp(),
                    )

                    val updated = incidentActions.updateNote(
                        note.id,
                        mapper.createObjectNode()
                            .put("status", InvestigationStatus.RESOLVED.name)
                            .put("public", true)
                            .putNull("timestamp"),
                    )

                    updated.status shouldBe InvestigationStatus.RESOLVED
                    updated.public shouldBe true
                    updated.timestamp.shouldBeNull()
                    updated.content shouldBe "Original"
                }
            }

            `when`("the incident of the note is tried to be changed") {

                then("those fields should be ignored") {
                    val event = downEvent()
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, event.id)

                    val updated = incidentActions.updateNote(
                        note.id,
                        mapper.createObjectNode()
                            .put("id", note.id + 1)
                            .put("incidentType", IncidentType.TCP.name)
                            .put("eventId", event.id + 1)
                            .put("content", "Changed"),
                    )

                    updated.id shouldBe note.id
                    updated.incidentType shouldBe IncidentType.HTTP
                    updated.eventId shouldBe event.id
                    updated.content shouldBe "Changed"
                }
            }

            `when`("the content would become blank") {

                then("it should throw a ValidationException, and keep the note as it was") {
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, downEvent().id, content = "Original")

                    shouldThrow<ValidationException> {
                        incidentActions.updateNote(note.id, mapper.createObjectNode().put("content", "  "))
                    }
                    incidentNoteRepository.findById(note.id).shouldNotBeNull().content shouldBe "Original"
                }
            }

            `when`("the status would be removed") {

                then("it should throw a ValidationException") {
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, downEvent().id)

                    shouldThrow<ValidationException> {
                        incidentActions.updateNote(note.id, mapper.createObjectNode().putNull("status"))
                    }
                }
            }

            `when`("the note doesn't exist") {

                then("it should throw an IncidentNoteNotFoundException") {
                    shouldThrow<IncidentNoteNotFoundException> {
                        incidentActions.updateNote(1234L, mapper.createObjectNode().put("content", "Changed"))
                    }
                }
            }
        }

        given("the deleteNote() method") {

            `when`("the note exists") {

                then("it should be deleted") {
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, downEvent().id)

                    incidentActions.deleteNote(note.id)

                    incidentNoteRepository.findById(note.id).shouldBeNull()
                }
            }

            `when`("the note doesn't exist") {

                then("it should throw an IncidentNoteNotFoundException") {
                    shouldThrow<IncidentNoteNotFoundException> { incidentActions.deleteNote(1234L) }
                }
            }
        }

        given("the eviction of the status page caches") {

            fun sslIncident() = createSSLEventRecord(
                dslContext,
                monitorId = createHttpMonitor(httpMonitorRepository).id,
                status = SslStatus.INVALID,
                startedAt = getCurrentTimestamp(),
                endedAt = null,
            )

            fun createDto(public: Boolean) =
                IncidentNoteCreateDto(status = InvestigationStatus.INVESTIGATING, content = "Checking", public = public)

            `when`("a note is created") {

                then("only a public note of an uptime incident should evict the caches") {
                    listOf(
                        Triple(IncidentType.HTTP, true, true),
                        Triple(IncidentType.HTTP, false, false),
                        Triple(IncidentType.SSL, true, false),
                    ).forEach { (type, public, shouldEvict) ->
                        val eventId = if (type == IncidentType.SSL) sslIncident().id else downEvent().id
                        fillStatusPageCaches()

                        incidentActions.createNote(type, eventId, createDto(public))

                        withClue("$type note, public: $public") {
                            statusPageCachesAreEvicted() shouldBe shouldEvict
                        }
                    }
                }
            }

            `when`("a note is updated") {

                then("the caches should be evicted if the note was public before or after the update") {
                    listOf(
                        Triple(false, false, false),
                        Triple(false, true, true),
                        Triple(true, false, true),
                        Triple(true, true, true),
                    ).forEach { (publicBefore, publicAfter, shouldEvict) ->
                        val note = createIncidentNote(
                            dslContext,
                            IncidentType.HTTP,
                            downEvent().id,
                            public = publicBefore,
                        )
                        fillStatusPageCaches()

                        incidentActions.updateNote(note.id, mapper.createObjectNode().put("public", publicAfter))

                        withClue("public before: $publicBefore, after: $publicAfter") {
                            statusPageCachesAreEvicted() shouldBe shouldEvict
                        }
                    }
                }
            }

            `when`("a public note of an SSL incident is updated") {

                then("the caches should be kept, as SSL incidents are not shown on status pages") {
                    val note = createIncidentNote(dslContext, IncidentType.SSL, sslIncident().id, public = true)
                    fillStatusPageCaches()

                    incidentActions.updateNote(note.id, mapper.createObjectNode().put("content", "Changed"))

                    statusPageCachesAreEvicted() shouldBe false
                }
            }

            `when`("a note is deleted") {

                then("only a public note of an uptime incident should evict the caches") {
                    listOf(
                        Triple(IncidentType.HTTP, true, true),
                        Triple(IncidentType.HTTP, false, false),
                        Triple(IncidentType.SSL, true, false),
                    ).forEach { (type, public, shouldEvict) ->
                        val eventId = if (type == IncidentType.SSL) sslIncident().id else downEvent().id
                        val note = createIncidentNote(dslContext, type, eventId, public = public)
                        fillStatusPageCaches()

                        incidentActions.deleteNote(note.id)

                        withClue("$type note, public: $public") {
                            statusPageCachesAreEvicted() shouldBe shouldEvict
                        }
                    }
                }
            }
        }
    }
}
