package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.MAINTENANCE_WINDOW_NAME_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MaintenanceWindowDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceException
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

@Singleton
@Suppress("TooManyFunctions")
class MaintenanceWindowRepository(private val dslContext: DSLContext) {

    fun findById(id: Long, txCtx: DSLContext = dslContext): MaintenanceWindowRecord? = txCtx
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ID.eq(id))
        .fetchOne()

    @Suppress("IgnoredReturnValue")
    fun fetchAll(sortedBy: SortField<*>? = null): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .apply { sortedBy?.let { orderBy(it, MAINTENANCE_WINDOW.ID.asc()) } }
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ENABLED.eq(enabled))
        .fetch()

    fun fetchEnabledOnStatusPages(): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
        .and(MAINTENANCE_WINDOW.SHOW_ON_STATUS_PAGES.eq(true))
        .fetch()

    /**
     * The enabled windows that may affect the given monitor: the global ones, the ones listing it explicitly, and the
     * ones covering the category it currently belongs to. The three are additive, and an uncategorized monitor can
     * only be reached by the first two.
     */
    fun findActiveCandidatesForMonitor(
        monitorId: MonitorID,
        category: String?,
    ): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
        .and(
            DSL.or(
                listOfNotNull(
                    MAINTENANCE_WINDOW.GLOBAL.eq(true),
                    MAINTENANCE_WINDOW.MONITORS.contains(arrayOf(monitorId)),
                    category?.let { MAINTENANCE_WINDOW.CATEGORIES.contains(arrayOf(it)) },
                )
            )
        )
        .fetch()

    /**
     * Batch variant of [findActiveCandidatesForMonitor]: for the given monitors, returns the enabled windows that
     * affect each of them (global, explicitly assigned, or covered by their category) in a single query, keyed by
     * monitor. Every requested monitor gets an entry, even if no window affects it.
     *
     * The monitors are passed in as their IDs mapped to the category they currently belong to, which is unnested
     * into a two-column derived table to keep the whole resolution on the database side.
     */
    fun findActiveCandidatesForMonitors(
        monitorsWithCategory: Map<MonitorID, String?>,
    ): Map<MonitorID, List<MaintenanceWindowRecord>> {
        if (monitorsWithCategory.isEmpty()) return emptyMap()

        val requestedIds = monitorsWithCategory.keys.map { it.toString() }.toTypedArray()
        val requestedCategories = monitorsWithCategory.values.toTypedArray()
        val monitorIdField = DSL.field("m.monitor_id", SQLDataType.CLOB)
        val categoryField = DSL.field("m.category", SQLDataType.CLOB)

        val windowsByMonitorId = dslContext
            .select(listOf(monitorIdField) + MAINTENANCE_WINDOW.fields().toList())
            .from(MAINTENANCE_WINDOW)
            .crossJoin(
                DSL.table(
                    "unnest({0}, {1})",
                    DSL.`val`(requestedIds, SQLDataType.CLOB.array()),
                    DSL.`val`(requestedCategories, SQLDataType.CLOB.array()),
                ).`as`("m", "monitor_id", "category")
            )
            .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
            .and(
                MAINTENANCE_WINDOW.GLOBAL.eq(true)
                    // MONITORS is a converted MonitorID[], so it cannot be compared to the unnested text column
                    // through the DSL, unlike the plain text[] of CATEGORIES right below
                    .or(DSL.condition("{0} @> array[{1}]", MAINTENANCE_WINDOW.MONITORS, monitorIdField))
                    .or(
                        categoryField.isNotNull
                            .and(MAINTENANCE_WINDOW.CATEGORIES.contains(DSL.array(categoryField)))
                    )
            )
            .fetchGroups({ it.get(monitorIdField) }, { it.into(MAINTENANCE_WINDOW) })

        return monitorsWithCategory.keys.associateWith { windowsByMonitorId[it.toString()].orEmpty() }
    }

    fun fetchDistinctCategories(): List<String> = dslContext
        .select(MAINTENANCE_WINDOW.CATEGORIES)
        .from(MAINTENANCE_WINDOW)
        .fetch(MAINTENANCE_WINDOW.CATEGORIES)
        .flatMap { it.toList() }
        .distinct()

    fun deleteById(id: Long, ctx: DSLContext = dslContext): Int = ctx
        .deleteFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ID.eq(id))
        .execute()

    fun returningInsert(maintenanceWindow: MaintenanceWindowRecord): MaintenanceWindowRecord =
        try {
            dslContext
                .insertInto(MAINTENANCE_WINDOW)
                .set(maintenanceWindow)
                .returning(MAINTENANCE_WINDOW.asterisk())
                .fetchOneOrThrow<MaintenanceWindowRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun returningUpdate(
        updatedWindow: MaintenanceWindowRecord,
        txCtx: DSLContext = dslContext,
    ): MaintenanceWindowRecord =
        try {
            txCtx
                .update(MAINTENANCE_WINDOW)
                .set(MAINTENANCE_WINDOW.NAME, updatedWindow.name)
                .set(MAINTENANCE_WINDOW.DESCRIPTION, updatedWindow.description)
                .set(MAINTENANCE_WINDOW.ENABLED, updatedWindow.enabled)
                .set(MAINTENANCE_WINDOW.GLOBAL, updatedWindow.global)
                .set(MAINTENANCE_WINDOW.SHOW_ON_STATUS_PAGES, updatedWindow.showOnStatusPages)
                .set(MAINTENANCE_WINDOW.CRON, updatedWindow.cron)
                .set(MAINTENANCE_WINDOW.START, updatedWindow.start)
                .set(MAINTENANCE_WINDOW.DURATION, updatedWindow.duration)
                .set(MAINTENANCE_WINDOW.MONITORS, updatedWindow.monitors)
                .set(MAINTENANCE_WINDOW.CATEGORIES, updatedWindow.categories)
                .set(MAINTENANCE_WINDOW.INTEGRATIONS, updatedWindow.integrations)
                .set(MAINTENANCE_WINDOW.UPDATED_AT, getCurrentTimestamp())
                .where(MAINTENANCE_WINDOW.ID.eq(updatedWindow.id))
                .returning(MAINTENANCE_WINDOW.asterisk())
                .fetchOneOrThrow<MaintenanceWindowRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    /**
     * Inserts a new maintenance window or updates an existing one if the name already exists.
     */
    fun upsert(
        maintenanceWindow: MaintenanceWindowRecord,
        txCtx: DSLContext = this.dslContext,
    ): MaintenanceWindowRecord = txCtx
        .insertInto(MAINTENANCE_WINDOW)
        .set(maintenanceWindow)
        .onConflictOnConstraint(MAINTENANCE_WINDOW_NAME_KEY)
        .doUpdate()
        .setNonKeyToExcluded()
        .set(MAINTENANCE_WINDOW.UPDATED_AT, getCurrentTimestamp())
        .returning(MAINTENANCE_WINDOW.asterisk())
        .fetchOneOrThrow()

    fun updateIntegrations(windowId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(MAINTENANCE_WINDOW)
            .set(MAINTENANCE_WINDOW.INTEGRATIONS, newIntegrations)
            .where(MAINTENANCE_WINDOW.ID.eq(windowId))
            .execute()
    }

    /**
     * Deletes all maintenance windows except the ones with the given IDs and returns the deleted windows' names.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): List<String> = txCtx
        .deleteFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ID.notIn(ignoredIds))
        .returning(MAINTENANCE_WINDOW.NAME)
        .fetch()
        .map { it.name }

    private fun DataAccessException.checkForDuplication(): PersistenceException =
        when (val persistenceException = toPersistenceException()) {
            is DuplicationException -> MaintenanceWindowDuplicatedException()
            else -> persistenceException
        }
}
