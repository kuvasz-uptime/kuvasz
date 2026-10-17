package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import java.time.OffsetDateTime

/**
 * The IDs of the monitors in the order the v3 list endpoints are expected to return them, when they are sorted by the
 * value [key] selects: the monitors without a value come last in both orders, and the ones with the same value are
 * sorted by their ID. Text values have to be lower-cased by [key], since the sorting ignores the case of the letters.
 */
internal fun <D : MonitorDetailsDto> List<D>.expectedIdsSortedBy(
    order: SortOrder,
    key: (D) -> Comparable<*>?,
): List<Long> {
    val (withValue, withoutValue) = partition { key(it) != null }
    val byValue = compareBy<D> { key(it) }.let { if (order == SortOrder.DESC) it.reversed() else it }
    return withValue.sortedWith(byValue.thenBy { it.id }).map { it.id } + withoutValue.map { it.id }.sorted()
}

/**
 * Fails if the monitors can't tell the sort fields apart: if two fields put them in the same order, or a field puts
 * them in the same order in both directions, a field sorting by the wrong column (or not at all) would go unnoticed.
 */
internal fun <D : MonitorDetailsDto, F> List<D>.shouldTellTheSortFieldsApart(keys: Map<F, (D) -> Comparable<*>?>) {
    val ascendingOrders = keys.mapValues { (_, key) -> expectedIdsSortedBy(SortOrder.ASC, key) }
    ascendingOrders.values.toSet() shouldHaveSize keys.size
    keys.forEach { (field, key) ->
        withClue(field) {
            expectedIdsSortedBy(SortOrder.DESC, key) shouldNotBe ascendingOrders.getValue(field)
        }
    }
}

/** The open uptime event of a monitor seeded for the sorting tests, its times given in minutes before now. */
internal data class SeededUptime(
    val status: UptimeStatus,
    val startedMinutesAgo: Long,
    val lastCheckMinutesAgo: Long,
)

/** Overrides the creation and the update time of a monitor, which the fixtures set to the time of the insert. */
internal fun <R : Record> DSLContext.setCreatedAndUpdatedAt(
    idField: TableField<R, Long>,
    createdAtField: TableField<R, OffsetDateTime>,
    updatedAtField: TableField<R, OffsetDateTime>,
    monitorId: Long,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    update(checkNotNull(idField.table))
        .set(createdAtField, createdAt)
        .set(updatedAtField, updatedAt)
        .where(idField.eq(monitorId))
        .execute()
}
