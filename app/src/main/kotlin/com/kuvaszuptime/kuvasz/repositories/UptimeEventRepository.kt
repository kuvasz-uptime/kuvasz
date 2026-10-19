package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.services.MonitorUptimeAggregate
import com.kuvaszuptime.kuvasz.services.UptimeAggregate
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.services.UptimeTimelineAggregate
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import java.math.BigDecimal
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
     * Fetches the incidents (DOWN events) of the given monitors that were open at any point of the period: the ones
     * that started by its end, and either haven't ended yet or ended after its start. Both ends are passed in, so they
     * are anchored to the very same instant as the calculations relying on the incidents.
     */
    fun fetchIncidentsInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
    ): List<UptimeEventCalculationContext>

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime?

    /**
     * Adds up the uptime events of the period on the database side, see [UptimeAggregate] for the rules. Only the
     * figures leave the database, so the size of the history doesn't matter for the memory of the application.
     *
     * @param monitorIdsInMaintenance The monitors whose ongoing incidents are counted as the ones in maintenance.
     */
    fun aggregateInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>? = null,
        onlyEnabledMonitors: Boolean = false,
        monitorIdsInMaintenance: Collection<Long> = emptyList(),
    ): UptimeAggregate

    /**
     * Adds up how much uptime and downtime the events of the period contributed to each slot, and how many incidents
     * started in them, see [UptimeTimelineAggregate]. The first slot starts at the start of the period, the last one
     * ends at [periodEnd].
     */
    fun aggregateTimeline(
        slotStarts: List<OffsetDateTime>,
        periodEnd: OffsetDateTime,
        onlyEnabledMonitors: Boolean,
    ): List<UptimeTimelineAggregate>

    /**
     * The monitors with the most downtime during the period, and among them, with the most incidents, leaving out the
     * ones without any incident. Every monitor tied with the last one is returned too, so the caller can break the
     * ties in its own order.
     */
    fun fetchLeastReliableMonitors(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        limit: Int,
        onlyEnabledMonitors: Boolean,
    ): List<MonitorUptimeAggregate>

    /**
     * Adds up the uptime events of the period monitor by monitor, the same way as [aggregateInPeriod] does. The
     * monitors without any event in the period are left out.
     */
    fun aggregateByMonitor(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
    ): List<MonitorUptimeAggregate>

    /**
     * The days on which the given monitors had any uptime event in effect, as the indexes of their [dayStarts], by
     * monitor. Every day ends where the next one starts, the last one at [lastDayEnd]. Only the events of the period
     * are considered, the same ones as by [fetchIncidentsInPeriod], whether they were in effect on a day is decided by
     * [UptimeEventCalculationContext.wasEffectiveBetween].
     */
    fun fetchDaysWithEvents(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
        dayStarts: List<OffsetDateTime>,
        lastDayEnd: OffsetDateTime,
    ): Map<Long, Set<Int>>
}

/**
 * The columns of an uptime event table, and of the table of its monitors, which the queries shared by every monitor
 * type rely on, so they are only written once.
 */
internal class UptimeEventColumns(
    val eventId: TableField<*, Long>,
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

// Written this way, instead of coalescing the end with now(), so it can use the index of ended_at
private fun UptimeEventColumns.wasOpenDuring(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): List<Condition> =
    listOf(startedAt.lessOrEqual(periodEnd), endedAt.isNull.or(endedAt.greaterThan(periodStart)))

/** See [UptimeEventRepository.fetchIncidentsInPeriod]. */
internal fun DSLContext.fetchUptimeIncidentsInPeriod(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>,
): List<UptimeEventCalculationContext> = with(columns) {
    select(
        eventId.`as`(UptimeEventCalculationContext::id.name),
        monitorId.`as`(UptimeEventCalculationContext::monitorId.name),
        monitorEnabled.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
        status.`as`(UptimeEventCalculationContext::status.name),
        startedAt.`as`(UptimeEventCalculationContext::startedAt.name),
        endedAt.`as`(UptimeEventCalculationContext::endedAt.name),
        updatedAt.`as`(UptimeEventCalculationContext::updatedAt.name),
    )
        .from(events)
        .join(monitors).on(eventMonitorId.eq(monitorId))
        .where(wasOpenDuring(periodStart, periodEnd))
        .and(status.eq(UptimeStatus.DOWN))
        .and(eventMonitorId.`in`(monitorIds))
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

/**
 * The uptime events of a period, clipped to it, as a derived table: an event starts at the start of the period the
 * earliest, and an ongoing one ends at the end of the period, or at its last update if its monitor is paused (see
 * [com.kuvaszuptime.kuvasz.util.getEffectiveEndOfEvent]). The events of a paused monitor that were last updated before
 * the period are left out, since they weren't effective during it.
 */
private class ClippedUptimeEvents(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>?,
    onlyEnabledMonitors: Boolean,
) {
    private val monitorIdColumn = columns.eventMonitorId.`as`("monitor_id")
    private val statusColumn = columns.status.`as`("status")
    private val startedAtColumn = columns.startedAt.`as`("started_at")
    private val endedAtColumn = columns.endedAt.`as`("ended_at")
    private val clippedStartColumn = DSL.greatest(columns.startedAt, DSL.`val`(periodStart)).`as`("clipped_start")
    private val clippedEndColumn = DSL.coalesce(
        columns.endedAt,
        DSL.`when`(columns.monitorEnabled.isTrue, DSL.`val`(periodEnd)).otherwise(columns.updatedAt),
    ).`as`("clipped_end")

    val table: Table<*> = with(columns) {
        DSL.select(monitorIdColumn, statusColumn, startedAtColumn, endedAtColumn, clippedStartColumn, clippedEndColumn)
            .from(events)
            .join(monitors).on(eventMonitorId.eq(monitorId))
            .where(
                wasOpenDuring(periodStart, periodEnd) + listOfNotNull(
                    monitorEnabled.isTrue.or(updatedAt.greaterOrEqual(periodStart)),
                    monitorIds?.let { eventMonitorId.`in`(it) },
                    monitorEnabled.isTrue.takeIf { onlyEnabledMonitors },
                )
            )
            .asTable("clipped_events")
    }
    val monitorId: Field<Long> = checkNotNull(table.field(monitorIdColumn))
    val status: Field<UptimeStatus> = checkNotNull(table.field(statusColumn))
    val startedAt: Field<OffsetDateTime> = checkNotNull(table.field(startedAtColumn))
    val endedAt: Field<OffsetDateTime> = checkNotNull(table.field(endedAtColumn))
    val clippedStart: Field<OffsetDateTime> = checkNotNull(table.field(clippedStartColumn))
    val clippedEnd: Field<OffsetDateTime> = checkNotNull(table.field(clippedEndColumn))

    val isUp: Condition = status.eq(UptimeStatus.UP)
    val isDown: Condition = status.eq(UptimeStatus.DOWN)

    // The duration of an event in the history, measured between the whole seconds of its ends, see
    // com.kuvaszuptime.kuvasz.util.getDurationOfEvent
    val durationSeconds: Field<Long> = epochSecondsBetween(clippedStart, clippedEnd)

    val uptimeSeconds: Field<Long> = DSL.sum(durationSeconds).filterWhere(isUp).orZero()
    val downtimeSeconds: Field<Long> = DSL.sum(durationSeconds).filterWhere(isDown).orZero()
    val incidents: Field<Int> = DSL.count().filterWhere(isDown)
}

/** Whole seconds between the epoch seconds of two instants, like `end.toEpochSecond() - start.toEpochSecond()`. */
private fun epochSecondsBetween(start: Field<OffsetDateTime>, end: Field<OffsetDateTime>): Field<Long> =
    DSL.field(
        "floor(extract(epoch from {0}))::bigint - floor(extract(epoch from {1}))::bigint",
        Long::class.java,
        end,
        start,
    )

/** Whole seconds of the span between two instants, like `Duration.between(start, end).seconds`. */
private fun secondsOfSpan(start: Field<OffsetDateTime>, end: Field<OffsetDateTime>): Field<Long> =
    DSL.field("floor(extract(epoch from {0}) - extract(epoch from {1}))::bigint", Long::class.java, end, start)

/**
 * The number of the slot an instant falls into: the 1-based index of the last one of the ascending [slotStarts] that
 * starts by it, 0 if it's before every one of them.
 */
private fun slotIndexOf(instant: Field<OffsetDateTime>, slotStarts: Field<Array<OffsetDateTime>>): Field<Int> =
    DSL.field("width_bucket({0}, {1})", Int::class.java, instant, slotStarts)

private fun Field<BigDecimal>.orZero(): Field<Long> = DSL.coalesce(this, BigDecimal.ZERO).cast(Long::class.java)

/** See [UptimeEventRepository.aggregateInPeriod]. */
internal fun DSLContext.aggregateUptimeInPeriod(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>?,
    onlyEnabledMonitors: Boolean,
    monitorIdsInMaintenance: Collection<Long>,
): UptimeAggregate = with(ClippedUptimeEvents(columns, periodStart, periodEnd, monitorIds, onlyEnabledMonitors)) {
    val isOngoingIncident = isDown.and(endedAt.isNull)
    val isResolvedIncident = isDown.and(endedAt.isNotNull)
    select(
        uptimeSeconds.`as`(UptimeAggregate::uptimeSeconds.name),
        downtimeSeconds.`as`(UptimeAggregate::downtimeSeconds.name),
        incidents.`as`(UptimeAggregate::incidents.name),
        DSL.countDistinct(monitorId).filterWhere(isDown).`as`(UptimeAggregate::affectedMonitors.name),
        DSL.count().filterWhere(isOngoingIncident).`as`(UptimeAggregate::ongoingIncidents.name),
        DSL.count().filterWhere(isOngoingIncident.and(monitorId.`in`(monitorIdsInMaintenance)))
            .`as`(UptimeAggregate::ongoingIncidentsInMaintenance.name),
        DSL.count().filterWhere(isResolvedIncident).`as`(UptimeAggregate::resolvedIncidents.name),
        DSL.sum(secondsOfSpan(startedAt, endedAt)).filterWhere(isResolvedIncident).orZero()
            .`as`(UptimeAggregate::resolvedIncidentSeconds.name),
    )
        .from(table)
        .fetchSingleInto(UptimeAggregate::class.java)
}

/**
 * Consecutive slots of time as a derived table, numbered from 1: every slot ends where the next one starts, the last
 * one at [lastEnd].
 */
private class ConsecutiveSlots(starts: List<OffsetDateTime>, lastEnd: OffsetDateTime) {
    private val startsWithIndex = DSL.unnest(starts).withOrdinality().`as`("slot_starts", "slot_start", "slot_index")
    private val startColumn = checkNotNull(startsWithIndex.field("slot_start", OffsetDateTime::class.java))
    private val indexColumn = checkNotNull(startsWithIndex.field("slot_index", Long::class.java))
    private val endColumn = DSL.coalesce(DSL.lead(startColumn).over(DSL.orderBy(indexColumn)), DSL.`val`(lastEnd))
        .`as`("slot_end")

    val table: Table<*> = DSL.select(startColumn, indexColumn, endColumn).from(startsWithIndex).asTable("slots")
    val index: Field<Long> = checkNotNull(table.field(indexColumn))
    val start: Field<OffsetDateTime> = checkNotNull(table.field(startColumn))
    val end: Field<OffsetDateTime> = checkNotNull(table.field(endColumn))
}

/** See [UptimeEventRepository.aggregateTimeline]. */
internal fun DSLContext.aggregateUptimeTimeline(
    columns: UptimeEventColumns,
    slotStarts: List<OffsetDateTime>,
    periodEnd: OffsetDateTime,
    onlyEnabledMonitors: Boolean,
): List<UptimeTimelineAggregate> {
    val events = ClippedUptimeEvents(columns, slotStarts.first(), periodEnd, null, onlyEnabledMonitors)
    val slots = ConsecutiveSlots(slotStarts, periodEnd)
    val slotIndex = slots.index
    val slotStart = slots.start
    val slotEnd = slots.end

    return with(events) {
        // The timeline doesn't reach beyond its end, not even with the last update of a paused monitor
        val end = DSL.least(clippedEnd, DSL.`val`(periodEnd))
        val overlaps = end.greaterThan(slotStart).and(clippedStart.lessThan(slotEnd))
        val overlapSeconds = secondsOfSpan(DSL.greatest(clippedStart, slotStart), DSL.least(end, slotEnd))
        // An incident only counts in the slot where it started, otherwise a long outage would show up in every slot it
        // spans. The ones that started before the period start in the first slot, and nothing goes past the last one.
        val startsInSlot = clippedStart.greaterOrEqual(slotStart)
            .and(clippedStart.lessThan(slotEnd).or(slotIndex.eq(slotStarts.size.toLong())))
        // Every event is only paired with the slots from the one it starts in to the one it ends in, instead of every
        // slot, the conditions above still decide what it adds to them
        val slotStartsArray = DSL.`val`(slotStarts.toTypedArray())
        val firstSlot = slotIndexOf(clippedStart, slotStartsArray)
        val candidateSlots = DSL.generateSeries(firstSlot, DSL.greatest(firstSlot, slotIndexOf(end, slotStartsArray)))
            .`as`("candidate_slots", "slot_index")
        val candidateSlot = checkNotNull(candidateSlots.field("slot_index", Int::class.java))

        select(
            DSL.sum(overlapSeconds).filterWhere(isUp.and(overlaps)).orZero()
                .`as`(UptimeTimelineAggregate::uptimeSeconds.name),
            DSL.sum(overlapSeconds).filterWhere(isDown.and(overlaps)).orZero()
                .`as`(UptimeTimelineAggregate::downtimeSeconds.name),
            DSL.count().filterWhere(isDown.and(startsInSlot)).`as`(UptimeTimelineAggregate::incidents.name),
        )
            .from(slots.table)
            .leftJoin(table.crossJoin(DSL.lateral(candidateSlots)))
            .on(candidateSlot.cast(Long::class.java).eq(slotIndex))
            .groupBy(slotIndex)
            .orderBy(slotIndex)
            .fetchInto(UptimeTimelineAggregate::class.java)
    }
}

/** See [UptimeEventRepository.fetchLeastReliableMonitors]. */
internal fun DSLContext.fetchLeastReliableMonitors(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    limit: Int,
    onlyEnabledMonitors: Boolean,
): List<MonitorUptimeAggregate> =
    with(ClippedUptimeEvents(columns, periodStart, periodEnd, null, onlyEnabledMonitors)) {
        selectUptimeByMonitor(this)
            .having(incidents.greaterThan(0))
            .orderBy(downtimeSeconds.desc(), incidents.desc())
            .limit(limit)
            .withTies()
            .fetchInto(MonitorUptimeAggregate::class.java)
    }

/** See [UptimeEventRepository.aggregateByMonitor]. */
internal fun DSLContext.aggregateUptimeByMonitor(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>,
): List<MonitorUptimeAggregate> =
    selectUptimeByMonitor(ClippedUptimeEvents(columns, periodStart, periodEnd, monitorIds, onlyEnabledMonitors = false))
        .fetchInto(MonitorUptimeAggregate::class.java)

private fun DSLContext.selectUptimeByMonitor(events: ClippedUptimeEvents) = with(events) {
    select(
        monitorId.`as`(MonitorUptimeAggregate::monitorId.name),
        uptimeSeconds.`as`(MonitorUptimeAggregate::uptimeSeconds.name),
        downtimeSeconds.`as`(MonitorUptimeAggregate::downtimeSeconds.name),
        incidents.`as`(MonitorUptimeAggregate::incidents.name),
    )
        .from(table)
        .groupBy(monitorId)
}

/** See [UptimeEventRepository.fetchDaysWithEvents]. */
internal fun DSLContext.fetchUptimeDaysWithEvents(
    columns: UptimeEventColumns,
    periodStart: OffsetDateTime,
    periodEnd: OffsetDateTime,
    monitorIds: List<Long>,
    dayStarts: List<OffsetDateTime>,
    lastDayEnd: OffsetDateTime,
): Map<Long, Set<Int>> = with(columns) {
    val days = ConsecutiveSlots(dayStarts, lastDayEnd)
    val dayIndex = days.index.minus(1).cast(Int::class.java).`as`("day_index")
    // See UptimeEventCalculationContext.wasEffectiveBetween
    val wasEffectiveOnDay = startedAt.lessThan(days.end)
        .and(DSL.coalesce(endedAt, updatedAt).greaterOrEqual(days.start))

    selectDistinct(eventMonitorId, dayIndex)
        .from(events)
        .join(days.table).on(wasEffectiveOnDay)
        .where(wasOpenDuring(periodStart, periodEnd))
        .and(eventMonitorId.`in`(monitorIds))
        .fetchGroups(eventMonitorId, dayIndex)
        .mapValues { (_, indexes) -> indexes.toSet() }
}

/** The column of an event table that [field] sorts by, shared by the uptime and the SSL events. */
internal fun eventSortColumn(
    field: EventSortField,
    id: Field<Long>,
    status: Field<*>,
    startedAt: Field<OffsetDateTime>,
    endedAt: Field<OffsetDateTime>,
    updatedAt: Field<OffsetDateTime>,
): Field<*> = when (field) {
    EventSortField.ID -> id
    EventSortField.STATUS -> status
    EventSortField.STARTED_AT -> startedAt
    EventSortField.ENDED_AT -> endedAt
    EventSortField.UPDATED_AT -> updatedAt
}

internal fun EventSortField.toField(columns: UptimeEventColumns): Field<*> =
    eventSortColumn(this, columns.eventId, columns.status, columns.startedAt, columns.endedAt, columns.updatedAt)
