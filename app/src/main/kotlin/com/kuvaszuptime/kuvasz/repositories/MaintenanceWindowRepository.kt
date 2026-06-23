package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Keys.MAINTENANCE_WINDOW_NAME_KEY
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.DuplicationException
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

@Singleton
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

    /**
     * Returns the enabled windows that could affect the given monitor: either global ones or ones that explicitly
     * list the monitor. Whether such a window is *currently* active (for cron/single ones) is decided afterwards by
     * the [com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowCalculator].
     */
    fun findActiveCandidatesForMonitor(monitorId: MonitorID): List<MaintenanceWindowRecord> = dslContext
        .selectFrom(MAINTENANCE_WINDOW)
        .where(MAINTENANCE_WINDOW.ENABLED.eq(true))
        .and(
            MAINTENANCE_WINDOW.GLOBAL.eq(true)
                .or(MAINTENANCE_WINDOW.MONITORS.contains(arrayOf(monitorId)))
        )
        .fetch()

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
