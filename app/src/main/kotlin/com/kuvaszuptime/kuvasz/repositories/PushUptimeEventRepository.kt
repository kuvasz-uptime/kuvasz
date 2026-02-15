package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class PushUptimeEventRepository(private val dslContext: DSLContext) {

    private fun PushMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: PushUptimeMonitorEvent, ctx: DSLContext? = dslContext): PushUptimeEventRecord {
        val eventToInsert = PushUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is PushMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
            eventToInsert.failureCount = 1
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
        .returning(PUSH_UPTIME_EVENT.asterisk())
        .fetchOneOrThrow<PushUptimeEventRecord>()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(PUSH_UPTIME_EVENT)
        .where(PUSH_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(PUSH_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: PushUptimeMonitorEvent) = dslContext
        .update(PUSH_UPTIME_EVENT)
        .set(PUSH_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is PushMonitorDownEvent) {
                set(PUSH_UPTIME_EVENT.FAILURE_COUNT, PUSH_UPTIME_EVENT.FAILURE_COUNT + 1)

                // Update the error message only for manually signaled failures
                if (newEvent.isManual) {
                    set(PUSH_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
                }
            }
        }
        .where(PUSH_UPTIME_EVENT.ID.eq(eventId))
        .returning(PUSH_UPTIME_EVENT.asterisk())
        .fetchOneOrThrow<PushUptimeEventRecord>()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<PushUptimeEventDto> = dslContext
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
        .orderBy(PUSH_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) {
                limit(limit)
            }
        }
        .fetchInto(PushUptimeEventDto::class.java)

    /**
     * Fetches all uptime events that have ended or was open within the specified period.
     */
    @Suppress("IgnoredReturnValue")
    fun fetchAllInPeriod(period: Duration, monitorId: Long? = null): List<UptimeEventCalculationContext> {
        val periodStart = getCurrentTimestamp().minus(period)
        return dslContext
            .select(
                PUSH_MONITOR.ID.`as`(UptimeEventCalculationContext::monitorId.name),
                PUSH_MONITOR.ENABLED.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
                PUSH_UPTIME_EVENT.STATUS.`as`(UptimeEventCalculationContext::status.name),
                PUSH_UPTIME_EVENT.STARTED_AT.`as`(UptimeEventCalculationContext::startedAt.name),
                PUSH_UPTIME_EVENT.ENDED_AT.`as`(UptimeEventCalculationContext::endedAt.name),
                PUSH_UPTIME_EVENT.UPDATED_AT.`as`(UptimeEventCalculationContext::updatedAt.name),
            )
            .from(PUSH_UPTIME_EVENT)
            .join(PUSH_MONITOR).on(PUSH_UPTIME_EVENT.MONITOR_ID.eq(PUSH_MONITOR.ID))
            .where(DSL.coalesce(PUSH_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            .apply {
                monitorId?.let { and(PUSH_UPTIME_EVENT.MONITOR_ID.eq(it)) }
            }
            .fetchInto(UptimeEventCalculationContext::class.java)
    }

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime? = dslContext
        .select(DSL.max(DSL.coalesce(PUSH_UPTIME_EVENT.UPDATED_AT, PUSH_UPTIME_EVENT.STARTED_AT)))
        .from(PUSH_UPTIME_EVENT)
        .join(PUSH_MONITOR).on(PUSH_UPTIME_EVENT.MONITOR_ID.eq(PUSH_MONITOR.ID))
        .where(PUSH_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .and(PUSH_MONITOR.ENABLED.isTrue)
        .fetchAny(0, OffsetDateTime::class.java)
}
