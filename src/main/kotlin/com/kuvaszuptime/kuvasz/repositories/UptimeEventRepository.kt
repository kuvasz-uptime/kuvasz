package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Singleton
class UptimeEventRepository(private val dslContext: DSLContext) {

    fun insertFromMonitorEvent(event: UptimeMonitorEvent, ctx: DSLContext = dslContext) {
        val eventToInsert = UptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is MonitorDownEvent) {
            eventToInsert.error = event.toStructuredMessage().error
        }

        ctx.insertInto(UPTIME_EVENT)
            .set(eventToInsert)
            .execute()
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

    fun updateEventUpdatedAt(eventId: Long, updatedAt: OffsetDateTime) = dslContext
        .update(UPTIME_EVENT)
        .set(UPTIME_EVENT.UPDATED_AT, updatedAt)
        .where(UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun isMonitorUp(monitorId: Long): Boolean =
        getPreviousEventByMonitorId(monitorId)?.let { it.status == UptimeStatus.UP } ?: false

    fun getEventsByMonitorId(monitorId: Long): List<UptimeEventDto> = dslContext
        .select(
            UPTIME_EVENT.STATUS.`as`("status"),
            UPTIME_EVENT.ERROR.`as`("error"),
            UPTIME_EVENT.STARTED_AT.`as`("startedAt"),
            UPTIME_EVENT.ENDED_AT.`as`("endedAt"),
            UPTIME_EVENT.UPDATED_AT.`as`("updatedAt")
        )
        .from(UPTIME_EVENT)
        .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(UPTIME_EVENT.STARTED_AT.desc())
        .fetchInto(UptimeEventDto::class.java)
}
