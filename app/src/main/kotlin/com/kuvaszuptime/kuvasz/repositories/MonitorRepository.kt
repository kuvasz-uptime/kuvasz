package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.util.toPersistenceException
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SelectHavingStep
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

/**
 * A generic repository interface for common, non-specific monitor operations.
 */
interface MonitorRepository<R : MonitorRecord> {

    companion object {
        const val MONITOR_NAME_FIELD_NAME = "monitor_name"
    }

    fun findById(monitorId: Long, txCtx: DSLContext?): R?
    fun deleteById(monitorId: Long, txCtx: DSLContext?): Int

    val monitorNameField: Field<String?>
        get() = DSL.field("t.monitor_name", SQLDataType.VARCHAR).`as`(MONITOR_NAME_FIELD_NAME)

    val statusPagesSubselect: SelectHavingStep<out Record>
        get() = DSL
            .select(
                monitorNameField,
                DSL.arrayAgg(STATUS_PAGE.SLUG).`as`("slugs"),
            )
            .from(STATUS_PAGE)
            .crossJoin(
                DSL.unnest(STATUS_PAGE.MONITORS).`as`("t", MONITOR_NAME_FIELD_NAME)
            )
            .groupBy(monitorNameField)

    /**
     * Converts a DataAccessException to a PersistenceException by matching duplication errors.
     */
    fun DataAccessException.checkForDuplication(): PersistenceException =
        when (val persistenceException = toPersistenceException()) {
            is DuplicationException -> MonitorDuplicatedException()
            else -> persistenceException
        }
}
