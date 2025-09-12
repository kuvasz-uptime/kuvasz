package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import com.kuvaszuptime.kuvasz.jooq.Keys.STATUS_PAGE_SLUG_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.StatusPageDuplicatedException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceError
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

@Singleton
class StatusPageRepository(private val dslContext: DSLContext) {

    fun findBySlug(slug: String, enabled: Boolean, ctx: DSLContext = dslContext): StatusPageRecord? = ctx
        .selectFrom(STATUS_PAGE)
        .where(STATUS_PAGE.SLUG.eq(slug))
        .and(STATUS_PAGE.ENABLED.eq(enabled))
        .fetchOne()

    fun findById(id: Long): StatusPageRecord? = dslContext
        .selectFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.eq(id))
        .fetchOne()

    fun fetchAll(): List<StatusPageRecord> = dslContext
        .selectFrom(STATUS_PAGE)
        .fetch()

    fun deleteById(id: Long, ctx: DSLContext = dslContext): Int = ctx
        .deleteFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.eq(id))
        .execute()

    fun returningInsert(statusPage: StatusPageRecord): Either<PersistenceException, StatusPageRecord> =
        try {
            Either.Right(
                dslContext
                    .insertInto(STATUS_PAGE)
                    .set(statusPage)
                    .returning(STATUS_PAGE.asterisk())
                    .fetchOneOrThrow<StatusPageRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    fun returningUpdate(
        updatedStatusPage: StatusPageRecord,
        txCtx: DSLContext = dslContext,
    ): Either<PersistenceException, StatusPageRecord> =
        try {
            Either.Right(
                txCtx
                    .update(STATUS_PAGE)
                    .set(STATUS_PAGE.SLUG, updatedStatusPage.slug)
                    .set(STATUS_PAGE.TITLE, updatedStatusPage.title)
                    .set(STATUS_PAGE.ENABLED, updatedStatusPage.enabled)
                    .set(STATUS_PAGE.MONITORS, updatedStatusPage.monitors)
                    .set(STATUS_PAGE.UPDATED_AT, getCurrentTimestamp())
                    .where(STATUS_PAGE.ID.eq(updatedStatusPage.id))
                    .returning(STATUS_PAGE.asterisk())
                    .fetchOneOrThrow<StatusPageRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    /**
     * Inserts a new monitor or updates an existing one if the name already exists.
     */
    fun upsert(statusPage: StatusPageRecord, txCtx: DSLContext = this.dslContext): StatusPageRecord = txCtx
        .insertInto(STATUS_PAGE)
        .set(statusPage)
        .onConflictOnConstraint(STATUS_PAGE_SLUG_KEY)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(STATUS_PAGE.UPDATED_AT, getCurrentTimestamp())
        .returning(STATUS_PAGE.asterisk())
        .fetchOneOrThrow()

    fun updateMonitors(statusPageId: Long, newMonitors: Array<MonitorID>) {
        dslContext
            .update(STATUS_PAGE)
            .set(STATUS_PAGE.MONITORS, newMonitors)
            .where(STATUS_PAGE.ID.eq(statusPageId))
            .execute()
    }

    /**
     * Deletes all status pages except the ones with the given IDs.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.notIn(ignoredIds))
        .execute()

    /**
     * Converts a DataAccessException to a PersistenceException by matching duplication errors.
     */
    private fun DataAccessException.handle(): Either<PersistenceException, Nothing> {
        val persistenceError = toPersistenceError()
        return Either.Left(
            if (persistenceError is DuplicationException) {
                StatusPageDuplicatedException()
            } else {
                persistenceError
            }
        )
    }
}
