package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.PendingFailure.PENDING_FAILURE
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class PendingFailureRepository(private val dslContext: DSLContext) {

    fun createOrIncrement(monitorId: Long): PendingFailureRecord {
        return dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val existingRecord: PendingFailureRecord? = txCtx
                .selectFrom(PENDING_FAILURE)
                .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
                .fetchOne()

            if (existingRecord != null) {
                txCtx.update(PENDING_FAILURE)
                    .set(PENDING_FAILURE.FAILURE_COUNT, PENDING_FAILURE.FAILURE_COUNT + 1)
                    .set(PENDING_FAILURE.UPDATED_AT, getCurrentTimestamp())
                    .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
                    .returning(PENDING_FAILURE.asterisk())
                    .fetchOneOrThrow<PendingFailureRecord>()
            } else {
                dslContext.insertInto(PENDING_FAILURE)
                    .set(PENDING_FAILURE.MONITOR_ID, monitorId)
                    .set(PENDING_FAILURE.FAILURE_COUNT, 1)
                    .returning(PENDING_FAILURE.asterisk())
                    .fetchOneOrThrow<PendingFailureRecord>()
            }
        }
    }

    fun deleteByMonitorId(monitorId: Long, txCtx: DSLContext = dslContext) = txCtx
        .deleteFrom(PENDING_FAILURE)
        .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
        .execute()
}
