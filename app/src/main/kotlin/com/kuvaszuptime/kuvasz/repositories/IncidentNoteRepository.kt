package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.IncidentNote.INCIDENT_NOTE
import com.kuvaszuptime.kuvasz.jooq.tables.records.IncidentNoteRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL

@Singleton
class IncidentNoteRepository(private val dslContext: DSLContext) {

    private val noteOrder = listOf(
        DSL.coalesce(INCIDENT_NOTE.TIMESTAMP, INCIDENT_NOTE.CREATED_AT).asc(),
        INCIDENT_NOTE.ID.asc(),
    )

    fun findById(id: Long, txCtx: DSLContext = dslContext): IncidentNoteRecord? = txCtx
        .selectFrom(INCIDENT_NOTE)
        .where(INCIDENT_NOTE.ID.eq(id))
        .fetchOne()

    fun fetchByEvent(incidentType: IncidentType, eventId: Long): List<IncidentNoteDto> = dslContext
        .selectFrom(INCIDENT_NOTE)
        .where(INCIDENT_NOTE.INCIDENT_TYPE.eq(incidentType))
        .and(INCIDENT_NOTE.EVENT_ID.eq(eventId))
        .orderBy(noteOrder)
        .fetch()
        .map { it.toDto() }

    fun returningInsert(note: IncidentNoteRecord): IncidentNoteRecord = dslContext
        .insertInto(INCIDENT_NOTE)
        .set(note)
        .returning(INCIDENT_NOTE.asterisk())
        .fetchOneOrThrow<IncidentNoteRecord>()

    fun returningUpdate(note: IncidentNoteRecord, txCtx: DSLContext = dslContext): IncidentNoteRecord = txCtx
        .update(INCIDENT_NOTE)
        .set(INCIDENT_NOTE.STATUS, note.status)
        .set(INCIDENT_NOTE.CONTENT, note.content)
        .set(INCIDENT_NOTE.TIMESTAMP, note.timestamp)
        .set(INCIDENT_NOTE.PUBLIC, note.public)
        .set(INCIDENT_NOTE.UPDATED_AT, getCurrentTimestamp())
        .where(INCIDENT_NOTE.ID.eq(note.id))
        .returning(INCIDENT_NOTE.asterisk())
        .fetchOneOrThrow<IncidentNoteRecord>()

    fun deleteById(id: Long): IncidentNoteRecord? = dslContext
        .deleteFrom(INCIDENT_NOTE)
        .where(INCIDENT_NOTE.ID.eq(id))
        .returning(INCIDENT_NOTE.asterisk())
        .fetchOne()
}

fun IncidentNoteRecord.toDto() = IncidentNoteDto(
    id = id,
    incidentType = incidentType,
    eventId = eventId,
    status = status,
    content = content,
    timestamp = timestamp,
    public = public,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
