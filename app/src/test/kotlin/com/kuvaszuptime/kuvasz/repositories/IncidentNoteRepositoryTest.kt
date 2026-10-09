package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.IncidentNote.INCIDENT_NOTE
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class IncidentNoteRepositoryTest(
    httpMonitorRepository: HttpMonitorRepository,
    tcpMonitorRepository: TcpMonitorRepository,
    httpUptimeEventRepository: HttpUptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    incidentNoteRepository: IncidentNoteRepository,
) : DatabaseBehaviorSpec() {
    init {

        fun allNoteIds() = dslContext.select(INCIDENT_NOTE.ID).from(INCIDENT_NOTE).fetch(INCIDENT_NOTE.ID)

        given("the fetchByEvent() method") {

            `when`("the incident has notes with and without timestamps") {

                then("they should be ordered by their timestamp, or by their creation date without one") {
                    val now = getCurrentTimestamp()
                    val monitor = createHttpMonitor(httpMonitorRepository)
                    val event = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusHours(2),
                        endedAt = null,
                    )
                    val third = createIncidentNote(dslContext, IncidentType.HTTP, event.id, createdAt = now)
                    val first = createIncidentNote(
                        dslContext,
                        IncidentType.HTTP,
                        event.id,
                        // Created last, but backdated
                        createdAt = now.plusMinutes(1),
                        timestamp = now.minusHours(1),
                    )
                    val second = createIncidentNote(
                        dslContext,
                        IncidentType.HTTP,
                        event.id,
                        createdAt = now.minusMinutes(30),
                    )
                    // The note of another type of incident with the very same event ID
                    createIncidentNote(dslContext, IncidentType.SSL, event.id)

                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, event.id)
                        .map { it.id } shouldBe listOf(first.id, second.id, third.id)
                }
            }

            `when`("the incident has no notes") {

                then("it should return an empty list") {
                    incidentNoteRepository.fetchByEvent(IncidentType.HTTP, 1234L).shouldBeEmpty()
                }
            }
        }

        given("the returningUpdate() method") {

            `when`("a note is updated") {

                then("its editable fields and its update date should change, but not its creation date") {
                    val createdAt = getCurrentTimestamp().minusDays(1)
                    val note = createIncidentNote(
                        dslContext,
                        IncidentType.HTTP,
                        eventId = 1L,
                        timestamp = getCurrentTimestamp(),
                        createdAt = createdAt,
                    )

                    val updated = incidentNoteRepository.returningUpdate(
                        note.copy().apply {
                            id = note.id
                            status = InvestigationStatus.RESOLVED
                            content = "Fixed"
                            timestamp = null
                            public = true
                        }
                    )

                    updated.status shouldBe InvestigationStatus.RESOLVED
                    updated.content shouldBe "Fixed"
                    updated.timestamp.shouldBeNull()
                    updated.public shouldBe true
                    updated.createdAt shouldBe note.createdAt
                    updated.updatedAt shouldBeAfter note.updatedAt
                }
            }
        }

        given("the deleteById() method") {

            `when`("the note exists") {

                then("it should be deleted, and returned as it was") {
                    val note = createIncidentNote(dslContext, IncidentType.HTTP, eventId = 1L, public = true)

                    incidentNoteRepository.deleteById(note.id).shouldNotBeNull().let { deleted ->
                        deleted.id shouldBe note.id
                        deleted.public shouldBe true
                    }
                    incidentNoteRepository.findById(note.id).shouldBeNull()
                }
            }

            `when`("the note doesn't exist") {

                then("nothing should be returned") {
                    incidentNoteRepository.deleteById(1234L).shouldBeNull()
                }
            }
        }

        given("the deletion of the events") {

            `when`("old uptime and SSL events are cleaned up") {

                then("only the notes of the deleted events should be deleted") {
                    val now = getCurrentTimestamp()
                    val monitor = createHttpMonitor(httpMonitorRepository)
                    val oldEvent = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusDays(10),
                        endedAt = now.minusDays(9),
                    )
                    val recentEvent = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusHours(1),
                        endedAt = null,
                    )
                    val oldSslEvent = createSSLEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = SslStatus.INVALID,
                        startedAt = now.minusDays(10),
                        endedAt = now.minusDays(9),
                    )
                    createIncidentNote(dslContext, IncidentType.HTTP, oldEvent.id)
                    createIncidentNote(dslContext, IncidentType.SSL, oldSslEvent.id)
                    val keptNotes = listOf(
                        createIncidentNote(dslContext, IncidentType.HTTP, recentEvent.id),
                        // Another type of incident, with the ID of a deleted event
                        createIncidentNote(dslContext, IncidentType.TCP, oldEvent.id),
                        createIncidentNote(dslContext, IncidentType.HTTP, oldSslEvent.id),
                    )

                    httpUptimeEventRepository.deleteEventsBeforeDate(now.minusDays(7))
                    sslEventRepository.deleteEventsBeforeDate(now.minusDays(7))

                    allNoteIds() shouldContainExactlyInAnyOrder keptNotes.map { it.id }
                }
            }

            `when`("a monitor is deleted, along with its events") {

                then("the notes of its incidents should be deleted too") {
                    val monitor = createTcpMonitor(tcpMonitorRepository)
                    val otherMonitor = createTcpMonitor(tcpMonitorRepository)
                    val event = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )
                    val otherEvent = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = otherMonitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )
                    createIncidentNote(dslContext, IncidentType.TCP, event.id)
                    val keptNote = createIncidentNote(dslContext, IncidentType.TCP, otherEvent.id)

                    tcpMonitorRepository.deleteById(monitor.id, null)

                    allNoteIds() shouldBe listOf(keptNote.id)
                }
            }
        }
    }
}
