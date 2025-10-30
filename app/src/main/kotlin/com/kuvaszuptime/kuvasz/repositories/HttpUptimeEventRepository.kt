package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class HttpUptimeEventRepository(private val dslContext: DSLContext) {

    private fun HttpMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: HttpUptimeMonitorEvent, ctx: DSLContext? = dslContext): HttpUptimeEventRecord {
        val eventToInsert = HttpUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is HttpMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(HTTP_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(HTTP_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<HttpUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<HttpUptimeEventRecord> = dslContext
        .selectFrom(HTTP_UPTIME_EVENT)
        .where(HTTP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): HttpUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx.selectFrom(HTTP_UPTIME_EVENT)
                .where(HTTP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(HTTP_UPTIME_EVENT.ENDED_AT.isNull)
                .orderBy(HTTP_UPTIME_EVENT.ID)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(HTTP_UPTIME_EVENT)
                    .where(HTTP_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(HTTP_UPTIME_EVENT)
        .set(HTTP_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(HTTP_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(HTTP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(HTTP_UPTIME_EVENT)
        .where(HTTP_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(HTTP_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: HttpUptimeMonitorEvent) = dslContext
        .update(HTTP_UPTIME_EVENT)
        .set(HTTP_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is HttpMonitorDownEvent) {
                set(HTTP_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(HTTP_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<HttpUptimeEventDto> = dslContext
        .select(
            HTTP_UPTIME_EVENT.ID.`as`(HttpUptimeEventDto::id.name),
            HTTP_UPTIME_EVENT.STATUS.`as`(HttpUptimeEventDto::status.name),
            HTTP_UPTIME_EVENT.ERROR.`as`(HttpUptimeEventDto::error.name),
            HTTP_UPTIME_EVENT.STARTED_AT.`as`(HttpUptimeEventDto::startedAt.name),
            HTTP_UPTIME_EVENT.ENDED_AT.`as`(HttpUptimeEventDto::endedAt.name),
            HTTP_UPTIME_EVENT.UPDATED_AT.`as`(HttpUptimeEventDto::updatedAt.name),
        )
        .from(HTTP_UPTIME_EVENT)
        .where(HTTP_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(HTTP_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) {
                limit(limit)
            }
        }
        .fetchInto(HttpUptimeEventDto::class.java)

    /**
     * Fetches all uptime events that have ended or was open within the specified period.
     */
    @Suppress("IgnoredReturnValue")
    fun fetchAllInPeriod(period: Duration, monitorId: Long? = null): List<UptimeEventCalculationContext> {
        val periodStart = getCurrentTimestamp().minus(period)
        return dslContext
            .select(
                HTTP_MONITOR.ID.`as`(UptimeEventCalculationContext::monitorId.name),
                HTTP_MONITOR.ENABLED.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
                HTTP_UPTIME_EVENT.STATUS.`as`(UptimeEventCalculationContext::status.name),
                HTTP_UPTIME_EVENT.STARTED_AT.`as`(UptimeEventCalculationContext::startedAt.name),
                HTTP_UPTIME_EVENT.ENDED_AT.`as`(UptimeEventCalculationContext::endedAt.name),
                HTTP_UPTIME_EVENT.UPDATED_AT.`as`(UptimeEventCalculationContext::updatedAt.name),
            )
            .from(HTTP_UPTIME_EVENT)
            .join(HTTP_MONITOR).on(HTTP_UPTIME_EVENT.MONITOR_ID.eq(HTTP_MONITOR.ID))
            .where(DSL.coalesce(HTTP_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            .apply {
                monitorId?.let { and(HTTP_UPTIME_EVENT.MONITOR_ID.eq(it)) }
            }
            .fetchInto(UptimeEventCalculationContext::class.java)
    }

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime? = dslContext
        .select(DSL.max(DSL.coalesce(HTTP_UPTIME_EVENT.UPDATED_AT, HTTP_UPTIME_EVENT.STARTED_AT)))
        .from(HTTP_UPTIME_EVENT)
        .join(HTTP_MONITOR).on(HTTP_UPTIME_EVENT.MONITOR_ID.eq(HTTP_MONITOR.ID))
        .where(HTTP_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .and(HTTP_MONITOR.ENABLED.isTrue)
        .fetchAny(0, OffsetDateTime::class.java)
}
