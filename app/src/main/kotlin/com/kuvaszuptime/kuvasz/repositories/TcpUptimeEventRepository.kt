package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.TcpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Suppress("TooManyFunctions")
@Singleton
class TcpUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

    private val uptimeEventColumns = UptimeEventColumns(
        eventMonitorId = TCP_UPTIME_EVENT.MONITOR_ID,
        status = TCP_UPTIME_EVENT.STATUS,
        startedAt = TCP_UPTIME_EVENT.STARTED_AT,
        endedAt = TCP_UPTIME_EVENT.ENDED_AT,
        updatedAt = TCP_UPTIME_EVENT.UPDATED_AT,
        monitorId = TCP_MONITOR.ID,
        monitorEnabled = TCP_MONITOR.ENABLED,
    )

    private fun TcpMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: TcpUptimeMonitorEvent, ctx: DSLContext? = dslContext): TcpUptimeEventRecord {
        val eventToInsert = TcpUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is TcpMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(TCP_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(TCP_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<TcpUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<TcpUptimeEventRecord> = dslContext
        .selectFrom(TCP_UPTIME_EVENT)
        .where(TCP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): TcpUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx
                .selectFrom(TCP_UPTIME_EVENT)
                .where(TCP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(TCP_UPTIME_EVENT.ENDED_AT.isNull)
                .orderBy(TCP_UPTIME_EVENT.ID)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(TCP_UPTIME_EVENT)
                    .where(TCP_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(TCP_UPTIME_EVENT)
        .set(TCP_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(TCP_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(TCP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(TCP_UPTIME_EVENT)
        .where(TCP_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(TCP_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: TcpUptimeMonitorEvent) = dslContext
        .update(TCP_UPTIME_EVENT)
        .set(TCP_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is TcpMonitorDownEvent) {
                set(TCP_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(TCP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<TcpUptimeEventDto> = dslContext
        .select(
            TCP_UPTIME_EVENT.ID.`as`(TcpUptimeEventDto::id.name),
            TCP_UPTIME_EVENT.STATUS.`as`(TcpUptimeEventDto::status.name),
            TCP_UPTIME_EVENT.ERROR.`as`(TcpUptimeEventDto::error.name),
            TCP_UPTIME_EVENT.STARTED_AT.`as`(TcpUptimeEventDto::startedAt.name),
            TCP_UPTIME_EVENT.ENDED_AT.`as`(TcpUptimeEventDto::endedAt.name),
            TCP_UPTIME_EVENT.UPDATED_AT.`as`(TcpUptimeEventDto::updatedAt.name),
        )
        .from(TCP_UPTIME_EVENT)
        .where(TCP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(TCP_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) limit(limit)
        }
        .fetchInto(TcpUptimeEventDto::class.java)

    override fun fetchAllInPeriod(
        periodStart: OffsetDateTime,
        periodEnd: OffsetDateTime,
        monitorIds: List<Long>?,
        onlyEnabledMonitors: Boolean,
    ): List<UptimeEventCalculationContext> = dslContext.fetchUptimeEventsInPeriod(
        uptimeEventColumns,
        periodStart,
        periodEnd,
        monitorIds,
        onlyEnabledMonitors,
    )

    override fun fetchLatestIncidentTimestamp(): OffsetDateTime? =
        dslContext.fetchLatestUptimeIncidentTimestamp(uptimeEventColumns)
}
