package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.STATUS_PAGE_SLUG_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.StatusPageDuplicatedException
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.fetchPage
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.sortedIn
import com.kuvaszuptime.kuvasz.util.toPersistenceException
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

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
            sortedBy?.let { orderBy(it, STATUS_PAGE.ID.asc()) }
        }
        .fetch()

    fun fetchPage(
        public: Boolean?,
        name: String?,
        sorting: Sorting<StatusPageSortField>,
        pageRequest: PageRequest,
    ): PagedResult<StatusPageRecord> {
        val condition = DSL.and(
            listOfNotNull(
                public?.let { STATUS_PAGE.PUBLIC.eq(it) },
                containsIgnoreCaseCondition(STATUS_PAGE.TITLE, name),
            )
        )
        return dslContext
            .selectFrom(STATUS_PAGE)
            .where(condition)
            .fetchPage(
                countQuery = dslContext.selectCount().from(STATUS_PAGE).where(condition),
                pageRequest = pageRequest,
                orderBy = listOf(sorting.field.toField().sortedIn(sorting.order), STATUS_PAGE.ID.asc()),
                type = StatusPageRecord::class.java,
            )
    }

    private fun StatusPageSortField.toField(): Field<*> = when (this) {
        StatusPageSortField.ID -> STATUS_PAGE.ID
        StatusPageSortField.TITLE -> STATUS_PAGE.TITLE
        StatusPageSortField.SLUG -> STATUS_PAGE.SLUG
        StatusPageSortField.PUBLIC -> STATUS_PAGE.PUBLIC
        StatusPageSortField.CREATED_AT -> STATUS_PAGE.CREATED_AT
        StatusPageSortField.UPDATED_AT -> STATUS_PAGE.UPDATED_AT
    }

    fun fetchDistinctCategories(): List<String> = dslContext
        .select(STATUS_PAGE.CATEGORIES)
        .from(STATUS_PAGE)
        .fetch(STATUS_PAGE.CATEGORIES)
        .flatMap { it.toList() }
        .distinct()

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
                .set(STATUS_PAGE.CATEGORIES, updatedStatusPage.categories)
                .set(STATUS_PAGE.DISPLAY_CATEGORIES, updatedStatusPage.displayCategories)
                .set(STATUS_PAGE.THEME_BASE, updatedStatusPage.themeBase)
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
     * Deletes all status pages except the ones with the given IDs and returns the deleted pages' titles.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): List<String> = txCtx
        .deleteFrom(STATUS_PAGE)
        .where(STATUS_PAGE.ID.notIn(ignoredIds))
        .returning(STATUS_PAGE.TITLE)
        .fetch()
        .map { it.title }

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
