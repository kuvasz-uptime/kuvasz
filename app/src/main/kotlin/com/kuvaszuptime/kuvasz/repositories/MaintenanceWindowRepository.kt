package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.MAINTENANCE_WINDOW_NAME_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.MaintenanceWindowDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
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
        .apply { sortedBy?.let { orderBy(sortedBy) } }
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

    fun findActiveCandidatesForMonitor(monitorId: MonitorID): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
        .and(
            MAINTENANCE_WINDOW.GLOBAL.eq(true)
                .or(MAINTENANCE_WINDOW.MONITORS.contains(arrayOf(monitorId)))
        )
        .fetch()

    /**
     * Batch variant of [findActiveCandidatesForMonitor]: for the given monitors, returns the enabled windows that
     * affect each of them (global, or explicitly assigned) in a single query, keyed by monitor. Every requested monitor
     * gets an entry, even if no window affects it.
     */
    fun findActiveCandidatesForMonitors(
        monitorIds: List<MonitorID>,
    ): Map<MonitorID, List<MaintenanceWindowRecord>> {
        if (monitorIds.isEmpty()) return emptyMap()

        val requestedIds = monitorIds.mapTo(mutableSetOf()) { it.toString() }.toTypedArray()
        val monitorIdField = DSL.field("m.monitor_id", SQLDataType.CLOB)

        val windowsByMonitorId = dslContext
            .select(listOf(monitorIdField) + MAINTENANCE_WINDOW.fields().toList())
            .from(MAINTENANCE_WINDOW)
            .crossJoin(
                DSL.unnest(DSL.`val`(requestedIds, SQLDataType.CLOB.array())).`as`("m", "monitor_id")
            )
            .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
            .and(
                MAINTENANCE_WINDOW.GLOBAL.eq(true)
                    .or(DSL.condition("{0} @> array[{1}]", MAINTENANCE_WINDOW.MONITORS, monitorIdField))
            )
            .fetchGroups({ it.get(monitorIdField) }, { it.into(MAINTENANCE_WINDOW) })

        return monitorIds.associateWith { windowsByMonitorId[it.toString()].orEmpty() }
    }

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
     * Deletes all maintenance windows except the ones with the given IDs.
     */
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext = this.dslContext): Int = txCtx
        .deleteFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ID.notIn(ignoredIds))
        .execute()

    private fun DataAccessException.checkForDuplication(): PersistenceException =
        when (val persistenceException = toPersistenceException()) {
            is DuplicationException -> MaintenanceWindowDuplicatedException()
            else -> persistenceException
        }
}
