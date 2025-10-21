package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.STATUS_PAGE_SLUG_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.StatusPageDuplicatedException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceException
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException

@Singleton
class StatusPageRepository(private val dslContext: DSLContext) {

    @Suppress("IgnoredReturnValue")
    fun findBySlug(slug: String, public: Boolean? = null): StatusPageRecord? = dslContext
        .selectFrom(STATUS_PAGE)
        .where(STATUS_PAGE.SLUG.eq(slug))
        .apply { public?.let { and(STATUS_PAGE.PUBLIC.eq(public)) } }
        .fetchOne()

    fun findById(id: Long, txCtx: DSLContext = dslContext): StatusPageRecord? = txCtx
        .selectFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.eq(id))
        .fetchOne()

    @Suppress("IgnoredReturnValue")
    fun fetchAll(
        public: Boolean? = null,
        sortedBy: SortField<*>? = null,
    ): List<StatusPageRecord> = dslContext
        .selectFrom(STATUS_PAGE)
        .apply {
            public?.let { where(STATUS_PAGE.PUBLIC.eq(public)) }
            sortedBy?.let { orderBy(sortedBy) }
        }
        .fetch()

    fun deleteById(id: Long, ctx: DSLContext = dslContext): Int = ctx
        .deleteFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.eq(id))
        .execute()

    fun returningInsert(statusPage: StatusPageRecord): StatusPageRecord =
        try {
            dslContext
                .insertInto(STATUS_PAGE)
                .set(statusPage)
                .returning(STATUS_PAGE.asterisk())
                .fetchOneOrThrow<StatusPageRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun returningUpdate(
        updatedStatusPage: StatusPageRecord,
        txCtx: DSLContext = dslContext,
    ): StatusPageRecord =
        try {
            txCtx
                .update(STATUS_PAGE)
                .set(STATUS_PAGE.SLUG, updatedStatusPage.slug)
                .set(STATUS_PAGE.TITLE, updatedStatusPage.title)
                .set(STATUS_PAGE.CUSTOM_LOGO_URL, updatedStatusPage.customLogoUrl)
                .set(STATUS_PAGE.CUSTOM_FAVICON_URL, updatedStatusPage.customFaviconUrl)
                .set(STATUS_PAGE.PUBLIC, updatedStatusPage.public)
                .set(STATUS_PAGE.MONITORS, updatedStatusPage.monitors)
                .set(STATUS_PAGE.UPDATED_AT, getCurrentTimestamp())
                .where(STATUS_PAGE.ID.eq(updatedStatusPage.id))
                .returning(STATUS_PAGE.asterisk())
                .fetchOneOrThrow<StatusPageRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
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
    private fun DataAccessException.checkForDuplication(): PersistenceException =
        when (val persistenceException = toPersistenceException()) {
            is DuplicationException -> StatusPageDuplicatedException()
            else -> persistenceException
        }

    fun getStatusPagesOfMonitor(monitorId: MonitorID): List<StatusPageRecord> = dslContext
        .selectFrom(STATUS_PAGE)
        .where(STATUS_PAGE.MONITORS.contains(arrayOf(monitorId)))
        .fetch()
}
