package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.util.toPersistenceException
import org.jooq.Condition
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
@Suppress("ComplexInterface")
sealed interface MonitorRepository<R : MonitorRecord, D : MonitorDetailsDto> {

    companion object {
        const val MONITOR_NAME_FIELD_NAME = "monitor_name"

        // Deliberately not "category": the outer query already has the monitor's own category column in scope
        const val PAGE_CATEGORY_FIELD_NAME = "page_category"
        private const val SLUGS_BY_NAME_FIELD_NAME = "slugs"
        private const val SLUGS_BY_CATEGORY_FIELD_NAME = "category_slugs"
    }

    fun fetchAllWithDetails(
        enabled: Boolean? = null,
        monitorNames: List<String>? = null,
        categories: List<String>? = null,
    ): List<D>
    fun findById(monitorId: Long, txCtx: DSLContext?): R?
    fun findByName(name: String, txCtx: DSLContext? = null): R?
    fun deleteById(monitorId: Long, txCtx: DSLContext?): Int
    fun fetchByEnabled(enabled: Boolean): List<R>
    fun fetchDistinctCategories(): List<String>
    fun returningUpdate(updatedMonitor: R, txCtx: DSLContext? = null): R
    fun upsert(monitor: R, txCtx: DSLContext? = null): R
    fun deleteAllExcept(ignoredIds: List<Long>, txCtx: DSLContext? = null): List<MonitorIDWithName>

    val monitorNameField: Field<String?>
        get() = DSL.field("t.monitor_name", SQLDataType.VARCHAR).`as`(MONITOR_NAME_FIELD_NAME)

    val pageCategoryField: Field<String?>
        get() = DSL.field("tc.page_category", SQLDataType.VARCHAR).`as`(PAGE_CATEGORY_FIELD_NAME)

    val statusPagesSubselect: SelectHavingStep<out Record>
        get() = DSL
            .select(
                monitorNameField,
                DSL.arrayAgg(STATUS_PAGE.SLUG).`as`(SLUGS_BY_NAME_FIELD_NAME),
            )
            .from(STATUS_PAGE)
            .crossJoin(
                DSL.unnest(STATUS_PAGE.MONITORS).`as`("t", MONITOR_NAME_FIELD_NAME)
            )
            .groupBy(monitorNameField)

    /**
     * The counterpart of [statusPagesSubselect] for the pages that reference a monitor by one of its categories
     * instead of by its name. It is joined on the monitor's own category, and the two results are merged by
     * [statusPagesField].
     */
    val categoryStatusPagesSubselect: SelectHavingStep<out Record>
        get() = DSL
            .select(
                pageCategoryField,
                DSL.arrayAgg(STATUS_PAGE.SLUG).`as`(SLUGS_BY_CATEGORY_FIELD_NAME),
            )
            .from(STATUS_PAGE)
            .crossJoin(
                DSL.unnest(STATUS_PAGE.CATEGORIES).`as`("tc", PAGE_CATEGORY_FIELD_NAME)
            )
            .groupBy(pageCategoryField)

    /**
     * The slugs of the status pages a monitor appears on, no matter whether it got there by its name or by its
     * category. A page that references it both ways must contribute its slug only once, and Postgres has no
     * array-distinct that the DSL could express, hence the raw expression.
     */
    val statusPagesField: Field<Array<String>>
        get() = DSL.field(
            "array(select distinct unnest(coalesce({0}, array[]::text[]) || coalesce({1}, array[]::text[])))",
            SQLDataType.CLOB.array(),
            statusPagesSubselect.field(SLUGS_BY_NAME_FIELD_NAME),
            categoryStatusPagesSubselect.field(SLUGS_BY_CATEGORY_FIELD_NAME),
        )

    /**
     * The WHERE condition selecting the monitors of a status page: the ones referenced explicitly by their name,
     * plus every monitor belonging to one of the referenced categories. The two selectors are additive.
     *
     * Both being null means "no restriction at all", which is how the default status page collects every enabled
     * monitor. Both being empty selects nothing, which is what an empty custom page already did before the
     * categories existed.
     */
    fun selectionCondition(
        nameField: Field<String>,
        categoryField: Field<String>,
        monitorNames: List<String>?,
        categories: List<String>?,
    ): Condition? {
        if (monitorNames == null && categories == null) return null

        val selectors = listOfNotNull(
            monitorNames?.takeIf { it.isNotEmpty() }?.let { nameField.`in`(it) },
            categories?.takeIf { it.isNotEmpty() }?.let { categoryField.`in`(it) },
        )
        return if (selectors.isEmpty()) DSL.falseCondition() else DSL.or(selectors)
    }

    /**
     * Converts a DataAccessException to a PersistenceException by matching duplication errors.
     */
    fun DataAccessException.checkForDuplication(): PersistenceException =
        when (val persistenceException = toPersistenceException()) {
            is DuplicationException -> MonitorDuplicatedException()
            else -> persistenceException
        }
}
