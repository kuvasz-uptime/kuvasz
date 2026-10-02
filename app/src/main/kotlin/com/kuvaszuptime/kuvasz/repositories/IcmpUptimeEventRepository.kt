package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.IcmpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Suppress("TooManyFunctions")
@Singleton
class IcmpUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

    private val uptimeEventColumns = UptimeEventColumns(
        eventMonitorId = ICMP_UPTIME_EVENT.MONITOR_ID,
        status = ICMP_UPTIME_EVENT.STATUS,
        startedAt = ICMP_UPTIME_EVENT.STARTED_AT,
        endedAt = ICMP_UPTIME_EVENT.ENDED_AT,
        updatedAt = ICMP_UPTIME_EVENT.UPDATED_AT,
        monitorId = ICMP_MONITOR.ID,
        monitorEnabled = ICMP_MONITOR.ENABLED,
    )

    private fun IcmpMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: IcmpUptimeMonitorEvent, ctx: DSLContext? = dslContext): IcmpUptimeEventRecord {
        val eventToInsert = IcmpUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is IcmpMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(ICMP_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(ICMP_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<IcmpUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<IcmpUptimeEventRecord> = dslContext
        .selectFrom(ICMP_UPTIME_EVENT)
        .where(ICMP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): IcmpUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx
                .selectFrom(ICMP_UPTIME_EVENT)
                .where(ICMP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(ICMP_UPTIME_EVENT.ENDED_AT.isNull)
                .orderBy(ICMP_UPTIME_EVENT.ID)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(ICMP_UPTIME_EVENT)
                    .where(ICMP_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(ICMP_UPTIME_EVENT)
        .set(ICMP_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(ICMP_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(ICMP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(ICMP_UPTIME_EVENT)
        .where(ICMP_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(ICMP_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: IcmpUptimeMonitorEvent) = dslContext
        .update(ICMP_UPTIME_EVENT)
        .set(ICMP_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is IcmpMonitorDownEvent) {
                set(ICMP_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(ICMP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<IcmpUptimeEventDto> = dslContext
        .select(
            ICMP_UPTIME_EVENT.ID.`as`(IcmpUptimeEventDto::id.name),
            ICMP_UPTIME_EVENT.STATUS.`as`(IcmpUptimeEventDto::status.name),
            ICMP_UPTIME_EVENT.ERROR.`as`(IcmpUptimeEventDto::error.name),
            ICMP_UPTIME_EVENT.STARTED_AT.`as`(IcmpUptimeEventDto::startedAt.name),
            ICMP_UPTIME_EVENT.ENDED_AT.`as`(IcmpUptimeEventDto::endedAt.name),
            ICMP_UPTIME_EVENT.UPDATED_AT.`as`(IcmpUptimeEventDto::updatedAt.name),
        )
        .from(ICMP_UPTIME_EVENT)
        .where(ICMP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(ICMP_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) limit(limit)
        }
        .fetchInto(IcmpUptimeEventDto::class.java)

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
