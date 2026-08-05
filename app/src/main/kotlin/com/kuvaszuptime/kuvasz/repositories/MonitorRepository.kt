package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
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
 *
 * Decisions are worth knowing about here:
 *
 * - [monitorType] is an extension property instead of a member, so it keeps working on the implementations even when
 *   they are mocked in the tests. A mocked member would return nothing and break every consumer that collects these
 *   repositories as a bean list, like [com.kuvaszuptime.kuvasz.services.StatCalculator].
 */
sealed interface MonitorRepository<R : MonitorRecord, D : MonitorDetailsDto> {

    companion object {
        const val MONITOR_NAME_FIELD_NAME = "monitor_name"
    }

    fun fetchAllWithDetails(enabled: Boolean? = null, monitorNames: List<String>? = null): List<D>

    fun findById(monitorId: Long, txCtx: DSLContext?): R?
    fun deleteById(monitorId: Long, txCtx: DSLContext?): Int
    fun fetchByEnabled(enabled: Boolean): List<R>

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
