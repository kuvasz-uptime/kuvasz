package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class UptimeEventRepository(private val dslContext: DSLContext) {

    private fun MonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: UptimeMonitorEvent, ctx: DSLContext = dslContext): UptimeEventRecord {
        val eventToInsert = UptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is MonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return ctx.insertInto(UPTIME_EVENT)
            .set(eventToInsert)
            .returning(UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<UptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<UptimeEventRecord> = dslContext
        .selectFrom(UPTIME_EVENT)
        .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): UptimeEventRecord? = dslContext
        .selectFrom(UPTIME_EVENT)
        .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .and(UPTIME_EVENT.ENDED_AT.isNull)
        .fetchOne()

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(UPTIME_EVENT)
        .set(UPTIME_EVENT.ENDED_AT, endedAt)
        .set(UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(UPTIME_EVENT)
        .where(UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: UptimeMonitorEvent) = dslContext
        .update(UPTIME_EVENT)
        .set(UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is MonitorDownEvent) {
                set(UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun isMonitorUp(monitorId: Long, nullAsUp: Boolean = false): Boolean =
        getPreviousEventByMonitorId(monitorId)?.let { it.status == UptimeStatus.UP } ?: nullAsUp

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<UptimeEventDto> = dslContext
        .select(
            UPTIME_EVENT.ID.`as`(UptimeEventDto::id.name),
            UPTIME_EVENT.STATUS.`as`(UptimeEventDto::status.name),
            UPTIME_EVENT.ERROR.`as`(UptimeEventDto::error.name),
            UPTIME_EVENT.STARTED_AT.`as`(UptimeEventDto::startedAt.name),
            UPTIME_EVENT.ENDED_AT.`as`(UptimeEventDto::endedAt.name),
            UPTIME_EVENT.UPDATED_AT.`as`(UptimeEventDto::updatedAt.name),
        )
        .from(UPTIME_EVENT)
        .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) {
                limit(limit)
            }
        }
        .fetchInto(UptimeEventDto::class.java)

    /**
     * Fetches all uptime events that have ended or was open within the specified period and are associated with
     * enabled monitors.
     */
    fun fetchAllInPeriod(period: Duration): List<UptimeEventRecord> = dslContext
        .select(UPTIME_EVENT.asterisk())
        .from(UPTIME_EVENT)
        .join(MONITOR).on(UPTIME_EVENT.MONITOR_ID.eq(MONITOR.ID))
        .where(DSL.coalesce(UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(getCurrentTimestamp().minus(period)))
        .and(MONITOR.ENABLED.isTrue)
        .fetchInto(UptimeEventRecord::class.java)

    /**
     * Fetches the timestamp of the latest incident (DOWN status) for enabled monitors.
     */
    fun fetchLatestIncidentTimestamp(): OffsetDateTime? = dslContext
        .select(DSL.max(DSL.coalesce(UPTIME_EVENT.UPDATED_AT, UPTIME_EVENT.STARTED_AT)))
        .from(UPTIME_EVENT)
        .join(MONITOR).on(UPTIME_EVENT.MONITOR_ID.eq(MONITOR.ID))
        .where(UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .and(MONITOR.ENABLED.isTrue)
        .fetchAny(0, OffsetDateTime::class.java)
}
