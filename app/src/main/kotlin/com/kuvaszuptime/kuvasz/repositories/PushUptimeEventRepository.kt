package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.services.MonitorUptimeAggregate
import com.kuvaszuptime.kuvasz.services.UptimeAggregate
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.services.UptimeTimelineAggregate
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.fetchPage
import com.kuvaszuptime.kuvasz.util.sortedIn
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Singleton
class PushUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

    private val uptimeEventColumns = UptimeEventColumns(
        eventId = PUSH_UPTIME_EVENT.ID,
        eventMonitorId = PUSH_UPTIME_EVENT.MONITOR_ID,
        status = PUSH_UPTIME_EVENT.STATUS,
        startedAt = PUSH_UPTIME_EVENT.STARTED_AT,
        endedAt = PUSH_UPTIME_EVENT.ENDED_AT,
        updatedAt = PUSH_UPTIME_EVENT.UPDATED_AT,
        monitorId = PUSH_MONITOR.ID,
        monitorEnabled = PUSH_MONITOR.ENABLED,
    )

    private fun PushMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(
        event: PushUptimeMonitorEvent,
        ctx: DSLContext? = dslContext,
        integrations: Array<IntegrationID> = emptyArray(),
    ): PushUptimeEventRecord {
        val eventToInsert = PushUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)
            .setIntegrations(integrations)

        if (event is PushMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(PUSH_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(PUSH_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<PushUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<PushUptimeEventRecord> = dslContext
        .selectFrom(PUSH_UPTIME_EVENT)
        .where(PUSH_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long, txCtx: DSLContext = dslContext): PushUptimeEventRecord? {
        val uptimeRecords = txCtx
            .selectFrom(PUSH_UPTIME_EVENT)
            .where(PUSH_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
            .and(PUSH_UPTIME_EVENT.ENDED_AT.isNull)
            .orderBy(PUSH_UPTIME_EVENT.ID)
            .fetch()

        if (uptimeRecords.size <= 1) return uptimeRecords.firstOrNull()

        uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
            txCtx.deleteFrom(PUSH_UPTIME_EVENT)
                .where(PUSH_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                .execute()
        }

        return uptimeRecords.last()
    }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(PUSH_UPTIME_EVENT)
        .set(PUSH_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(PUSH_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(PUSH_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(PUSH_UPTIME_EVENT)
        .where(PUSH_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(PUSH_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: PushUptimeMonitorEvent, ctx: DSLContext = dslContext) = ctx
        .update(PUSH_UPTIME_EVENT)
        .set(PUSH_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            // Update the error message only for manually signaled failures
            if (newEvent is PushMonitorDownEvent && newEvent.isManual) {
                set(PUSH_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(PUSH_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<PushUptimeEventDto> =
        eventDtoSelect(monitorId)
            .orderBy(PUSH_UPTIME_EVENT.STARTED_AT.desc())
            .apply {
                if (limit != null) {
                    limit(limit)
                }
            }
            .fetchInto(PushUptimeEventDto::class.java)

    fun getEventsPage(
        monitorId: Long,
        sorting: Sorting<EventSortField>,
        pageRequest: PageRequest,
    ): PagedResult<PushUptimeEventDto> =
        eventDtoSelect(monitorId).fetchPage(
            countQuery = dslContext
                .selectCount()
                .from(PUSH_UPTIME_EVENT)
                .where(PUSH_UPTIME_EVENT.MONITOR_ID.eq(monitorId)),
            pageRequest = pageRequest,
            orderBy = listOf(
                sorting.field.toField(uptimeEventColumns).sortedIn(sorting.order),
                PUSH_UPTIME_EVENT.ID.asc(),
            ),
            type = PushUptimeEventDto::class.java,
        )

    private fun eventDtoSelect(monitorId: Long) = dslContext
        .select(
            PUSH_UPTIME_EVENT.ID.`as`(PushUptimeEventDto::id.name),
            PUSH_UPTIME_EVENT.STATUS.`as`(PushUptimeEventDto::status.name),
            PUSH_UPTIME_EVENT.ERROR.`as`(PushUptimeEventDto::error.name),
            PUSH_UPTIME_EVENT.STARTED_AT.`as`(PushUptimeEventDto::startedAt.name),
            PUSH_UPTIME_EVENT.ENDED_AT.`as`(PushUptimeEventDto::endedAt.name),
            PUSH_UPTIME_EVENT.UPDATED_AT.`as`(PushUptimeEventDto::updatedAt.name),
        )
        .from(PUSH_UPTIME_EVENT)
        .where(PUSH_UPTIME_EVENT.MONITOR_ID.eq(monitorId))

    override fun fetchIncidentsInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
    ): List<UptimeEventCalculationContext> =
        dslContext.fetchUptimeIncidentsInPeriod(uptimeEventColumns, periodStart, periodEnd, monitorIds)

    override fun fetchLatestIncidentTimestamp(): OffsetDateTime? =
        dslContext.fetchLatestUptimeIncidentTimestamp(uptimeEventColumns)

    override fun aggregateInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>?,
        onlyEnabledMonitors: Boolean,
        monitorIdsInMaintenance: Collection<Long>,
    ): UptimeAggregate = dslContext.aggregateUptimeInPeriod(
        uptimeEventColumns,
        periodStart,
        periodEnd,
        monitorIds,
        onlyEnabledMonitors,
        monitorIdsInMaintenance,
    )

    override fun aggregateTimeline(
        slotStarts: List<OffsetDateTime>,
        periodEnd: OffsetDateTime,
        onlyEnabledMonitors: Boolean,
    ): List<UptimeTimelineAggregate> =
        dslContext.aggregateUptimeTimeline(uptimeEventColumns, slotStarts, periodEnd, onlyEnabledMonitors)

    override fun fetchLeastReliableMonitors(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        limit: Int,
        onlyEnabledMonitors: Boolean,
    ): List<MonitorUptimeAggregate> =
        dslContext.fetchLeastReliableMonitors(uptimeEventColumns, periodStart, periodEnd, limit, onlyEnabledMonitors)

    override fun aggregateByMonitor(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
    ): List<MonitorUptimeAggregate> =
        dslContext.aggregateUptimeByMonitor(uptimeEventColumns, periodStart, periodEnd, monitorIds)

    override fun fetchDaysWithEvents(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>,
        dayStarts: List<OffsetDateTime>,
        lastDayEnd: OffsetDateTime,
    ): Map<Long, Set<Int>> = dslContext.fetchUptimeDaysWithEvents(
        uptimeEventColumns,
        periodStart,
        periodEnd,
        monitorIds,
        dayStarts,
        lastDayEnd,
    )
}
