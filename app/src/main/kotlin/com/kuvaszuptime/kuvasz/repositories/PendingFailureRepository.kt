package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.PendingFailure.PENDING_FAILURE
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class PendingFailureRepository(private val dslContext: DSLContext) {
    
    fun createOrIncrement(monitorId: Long, txCtx: DSLContext? = null): PendingFailureRecord {
        val now = getCurrentTimestamp()

        return (txCtx ?: dslContext)
            .insertInto(PENDING_FAILURE)
            .set(PENDING_FAILURE.MONITOR_ID, monitorId)
            .set(PENDING_FAILURE.FAILURE_COUNT, 1L)
            .set(PENDING_FAILURE.UPDATED_AT, now)
            .onConflict(PENDING_FAILURE.MONITOR_ID)
            .doUpdate()
            .set(PENDING_FAILURE.FAILURE_COUNT, PENDING_FAILURE.FAILURE_COUNT + 1)
            .set(PENDING_FAILURE.UPDATED_AT, now)
            .returning(PENDING_FAILURE.asterisk())
            .fetchOneOrThrow<PendingFailureRecord>()
    }

    fun deleteByMonitorId(monitorId: Long, txCtx: DSLContext? = null) = (txCtx ?: dslContext)
        .deleteFrom(PENDING_FAILURE)
        .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
        .execute()
}
