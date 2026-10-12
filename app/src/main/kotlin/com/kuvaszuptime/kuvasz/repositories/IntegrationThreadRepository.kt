package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.IntegrationThreadSubject
import com.kuvaszuptime.kuvasz.jooq.tables.IntegrationThread.INTEGRATION_THREAD
import com.kuvaszuptime.kuvasz.jooq.tables.records.IntegrationThreadRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class IntegrationThreadRepository(private val dslContext: DSLContext) {

    fun upsert(thread: IntegrationThreadRecord): Int = dslContext
        .insertInto(INTEGRATION_THREAD)
        .set(thread)
        .onConflict(INTEGRATION_THREAD.INTEGRATION_ID, INTEGRATION_THREAD.SUBJECT_TYPE, INTEGRATION_THREAD.SUBJECT_ID)
        .doUpdate()
        .set(INTEGRATION_THREAD.TS, thread.ts)
        .set(INTEGRATION_THREAD.ROOT_TEXT, thread.rootText)
        .execute()

    fun find(
        integrationId: IntegrationID,
        subjectType: IntegrationThreadSubject,
        subjectId: Long,
    ): IntegrationThreadRecord? = dslContext
        .selectFrom(INTEGRATION_THREAD)
        .where(INTEGRATION_THREAD.INTEGRATION_ID.eq(integrationId))
        .and(INTEGRATION_THREAD.SUBJECT_TYPE.eq(subjectType))
        .and(INTEGRATION_THREAD.SUBJECT_ID.eq(subjectId))
        .fetchOne()

    fun findBySubject(subjectType: IntegrationThreadSubject, subjectId: Long): List<IntegrationThreadRecord> =
        dslContext
            .selectFrom(INTEGRATION_THREAD)
            .where(INTEGRATION_THREAD.SUBJECT_TYPE.eq(subjectType))
            .and(INTEGRATION_THREAD.SUBJECT_ID.eq(subjectId))
            .fetch()

    fun delete(integrationId: IntegrationID, subjectType: IntegrationThreadSubject, subjectId: Long): Int = dslContext
        .deleteFrom(INTEGRATION_THREAD)
        .where(INTEGRATION_THREAD.INTEGRATION_ID.eq(integrationId))
        .and(INTEGRATION_THREAD.SUBJECT_TYPE.eq(subjectType))
        .and(INTEGRATION_THREAD.SUBJECT_ID.eq(subjectId))
        .execute()

    /**
     * Deletes the threads of every maintenance window, except the given (currently active) ones. A maintenance thread
     * is only valid between the start and the end of the window, so the rest of them are leftovers of an end event
     * that never reached the integration (e.g. it was unassigned from the window in the meantime), or of a deleted
     * window.
     */
    fun deleteMaintenanceThreadsExcept(activeWindowIds: Collection<Long>): Int = dslContext
        .deleteFrom(INTEGRATION_THREAD)
        .where(INTEGRATION_THREAD.SUBJECT_TYPE.eq(IntegrationThreadSubject.MAINTENANCE))
        .and(INTEGRATION_THREAD.SUBJECT_ID.notIn(activeWindowIds))
        .execute()
}
