package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import org.jooq.DSLContext
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import java.time.OffsetDateTime

/**
 * The monitor type agnostic view of the uptime event repositories, providing everything that is needed to calculate
 * the uptime related statistics of any monitor type.
 *
 * Decisions are worth knowing about here:
 *
 * - [monitorType] is an extension property instead of a member, so it keeps working on the implementations even when
 *   they are mocked in the tests. A mocked member would return nothing and break every consumer that collects these
 *   repositories as a bean list, like [com.kuvaszuptime.kuvasz.services.StatCalculator].
 */
sealed interface UptimeEventRepository {

    /**
     * Fetches the uptime events that were open at any point of the period: the ones that started by its end, and
     * either haven't ended yet or ended after its start. Both ends are passed in, so they are anchored to the very same
     * instant as the calculations relying on the events.
     *
     * @param onlyEnabledMonitors Whether the events of the monitors that are paused now should be left out.
     */
    fun fetchAllInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>? = null,
        onlyEnabledMonitors: Boolean = false,
    ): List<UptimeEventCalculationContext>

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime?
}

/**
 * The columns of an uptime event table, and of the table of its monitors, which the queries shared by every monitor
 * type rely on, so they are only written once.
 */
internal class UptimeEventColumns(
    val eventMonitorId: TableField<*, Long>,
    val status: TableField<*, UptimeStatus>,
    val startedAt: TableField<*, OffsetDateTime>,
    val endedAt: TableField<*, OffsetDateTime>,
    val updatedAt: TableField<*, OffsetDateTime>,
    val monitorId: TableField<*, Long>,
    val monitorEnabled: TableField<*, Boolean>,
) {
    val events: Table<*> get() = checkNotNull(status.table)
    val monitors: Table<*> get() = checkNotNull(monitorId.table)
}

/** See [UptimeEventRepository.fetchAllInPeriod]. */
internal fun DSLContext.fetchUptimeEventsInPeriod(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>?,
    onlyEnabledMonitors: Boolean,
): List<UptimeEventCalculationContext> = with(columns) {
    select(
        monitorId.`as`(UptimeEventCalculationContext::monitorId.name),
        monitorEnabled.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
        status.`as`(UptimeEventCalculationContext::status.name),
        startedAt.`as`(UptimeEventCalculationContext::startedAt.name),
        endedAt.`as`(UptimeEventCalculationContext::endedAt.name),
        updatedAt.`as`(UptimeEventCalculationContext::updatedAt.name),
    )
        .from(events)
        .join(monitors).on(eventMonitorId.eq(monitorId))
        .where(
            listOfNotNull(
                startedAt.lessOrEqual(periodEnd),
                // Written this way, instead of coalescing the end with now(), so it can use the index of ended_at
                endedAt.isNull.or(endedAt.greaterThan(periodStart)),
                monitorIds?.let { eventMonitorId.`in`(it) },
                monitorEnabled.isTrue.takeIf { onlyEnabledMonitors },
            )
        )
        .fetchInto(UptimeEventCalculationContext::class.java)
}

/**
 * See [UptimeEventRepository.fetchLatestIncidentTimestamp]. An ongoing incident is updated by every check, unlike a
 * resolved one, whose end is its last update too. So they are looked up separately, the ongoing ones through the index
 * of the open events, the resolved ones through the index of the end date, instead of indexing the update date, which
 * would make every check more expensive.
 */
internal fun DSLContext.fetchLatestUptimeIncidentTimestamp(columns: UptimeEventColumns): OffsetDateTime? =
    with(columns) {
        val latestOngoingIncident = select(DSL.max(updatedAt))
            .from(events)
            .join(monitors).on(eventMonitorId.eq(monitorId))
            .where(endedAt.isNull)
            .and(status.eq(UptimeStatus.DOWN))
            .and(monitorEnabled.isTrue)
        val latestResolvedIncident = select(endedAt)
            .from(events)
            .join(monitors).on(eventMonitorId.eq(monitorId))
            .where(endedAt.isNotNull)
            .and(status.eq(UptimeStatus.DOWN))
            .and(monitorEnabled.isTrue)
            .orderBy(endedAt.desc())
            .limit(1)

        select(DSL.greatest(DSL.field(latestOngoingIncident), DSL.field(latestResolvedIncident)))
            .fetchOne()
            ?.value1()
    }
