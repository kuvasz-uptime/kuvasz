package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.DockerUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerUptimeMonitorEvent
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

@Suppress("TooManyFunctions")
@Singleton
class DockerUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

    private val uptimeEventColumns = UptimeEventColumns(
        eventId = DOCKER_UPTIME_EVENT.ID,
        eventMonitorId = DOCKER_UPTIME_EVENT.MONITOR_ID,
        status = DOCKER_UPTIME_EVENT.STATUS,
        startedAt = DOCKER_UPTIME_EVENT.STARTED_AT,
        endedAt = DOCKER_UPTIME_EVENT.ENDED_AT,
        updatedAt = DOCKER_UPTIME_EVENT.UPDATED_AT,
        monitorId = DOCKER_MONITOR.ID,
        monitorEnabled = DOCKER_MONITOR.ENABLED,
    )

    private fun DockerMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(
        event: DockerUptimeMonitorEvent,
        ctx: DSLContext? = dslContext,
        integrations: Array<IntegrationID> = emptyArray(),
    ): DockerUptimeEventRecord {
        val eventToInsert = DockerUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setIntegrations(integrations)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)
            .setImage(event.image)

        // A check that could not inspect the container (an unreachable daemon, a vanished container) carries the known
        // restart count over, so that the restarts in between are still counted against it once the container is back.
        // The pair is carried over together, so that a count is never compared against another container's.
        if (event.restartCount != null && event.containerCreatedAt != null) {
            eventToInsert.setRestartCount(event.restartCount).setContainerCreatedAt(event.containerCreatedAt)
        } else {
            event.previousEvent?.let { previous ->
                eventToInsert.setRestartCount(previous.restartCount).setContainerCreatedAt(previous.containerCreatedAt)
            }
        }

        if (event is DockerMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(DOCKER_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(DOCKER_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<DockerUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<DockerUptimeEventRecord> = dslContext
        .selectFrom(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): DockerUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx
                .selectFrom(DOCKER_UPTIME_EVENT)
                .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(DOCKER_UPTIME_EVENT.ENDED_AT.isNull)
                .orderBy(DOCKER_UPTIME_EVENT.ID)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(DOCKER_UPTIME_EVENT)
                    .where(DOCKER_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun fetchLatestRestartCount(monitorId: Long): Int? = dslContext
        .select(DOCKER_UPTIME_EVENT.RESTART_COUNT)
        .from(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(DOCKER_UPTIME_EVENT.UPDATED_AT.desc(), DOCKER_UPTIME_EVENT.ID.desc())
        .limit(1)
        .fetchOne(DOCKER_UPTIME_EVENT.RESTART_COUNT)

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(DOCKER_UPTIME_EVENT)
        .set(DOCKER_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(DOCKER_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(DOCKER_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(DOCKER_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: DockerUptimeMonitorEvent, ctx: DSLContext = dslContext) = ctx
        .update(DOCKER_UPTIME_EVENT)
        .set(DOCKER_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            // A check that could not tell the image (a vanished container, an unreachable daemon) keeps the known one
            if (newEvent.image != null) {
                set(DOCKER_UPTIME_EVENT.IMAGE, newEvent.image)
            }
            if (newEvent.restartCount != null && newEvent.containerCreatedAt != null) {
                set(DOCKER_UPTIME_EVENT.RESTART_COUNT, newEvent.restartCount)
                set(DOCKER_UPTIME_EVENT.CONTAINER_CREATED_AT, newEvent.containerCreatedAt)
            }
            if (newEvent is DockerMonitorDownEvent) {
                set(DOCKER_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(DOCKER_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DockerUptimeEventDto> =
        eventDtoSelect(monitorId)
            .orderBy(DOCKER_UPTIME_EVENT.STARTED_AT.desc())
            .apply {
                if (limit != null) limit(limit)
            }
            .fetchInto(DockerUptimeEventDto::class.java)

    fun getEventsPage(
        monitorId: Long,
        sorting: Sorting<EventSortField>,
        pageRequest: PageRequest,
    ): PagedResult<DockerUptimeEventDto> =
        eventDtoSelect(monitorId).fetchPage(
            countQuery = dslContext
                .selectCount()
                .from(DOCKER_UPTIME_EVENT)
                .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId)),
            pageRequest = pageRequest,
            orderBy = listOf(
                sorting.field.toField(uptimeEventColumns).sortedIn(sorting.order),
                DOCKER_UPTIME_EVENT.ID.asc(),
            ),
            type = DockerUptimeEventDto::class.java,
        )

    private fun eventDtoSelect(monitorId: Long) = dslContext
        .select(
            DOCKER_UPTIME_EVENT.ID.`as`(DockerUptimeEventDto::id.name),
            DOCKER_UPTIME_EVENT.STATUS.`as`(DockerUptimeEventDto::status.name),
            DOCKER_UPTIME_EVENT.ERROR.`as`(DockerUptimeEventDto::error.name),
            DOCKER_UPTIME_EVENT.IMAGE.`as`(DockerUptimeEventDto::image.name),
            DOCKER_UPTIME_EVENT.RESTART_COUNT.`as`(DockerUptimeEventDto::restartCount.name),
            DOCKER_UPTIME_EVENT.CONTAINER_CREATED_AT.`as`(DockerUptimeEventDto::containerCreatedAt.name),
            DOCKER_UPTIME_EVENT.STARTED_AT.`as`(DockerUptimeEventDto::startedAt.name),
            DOCKER_UPTIME_EVENT.ENDED_AT.`as`(DockerUptimeEventDto::endedAt.name),
            DOCKER_UPTIME_EVENT.UPDATED_AT.`as`(DockerUptimeEventDto::updatedAt.name),
        )
        .from(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))

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
