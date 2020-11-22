package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.UptimeEventDao
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import org.jooq.Configuration
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import javax.inject.Singleton

@Singleton
class UptimeEventRepository(private val jooqConfig: Configuration) : UptimeEventDao(jooqConfig) {
    private val dsl = jooqConfig.dsl()

    fun insertFromMonitorEvent(event: UptimeMonitorEvent, configuration: Configuration? = jooqConfig) {
        val eventToInsert = UptimeEventPojo()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is MonitorDownEvent) {
            eventToInsert.error = event.error.message
        }

        DSL.using(configuration)
            .insertInto(UPTIME_EVENT)
            .set(dsl.newRecord(UPTIME_EVENT, eventToInsert))
            .execute()
    }

    fun getPreviousEventByMonitorId(monitorId: Int): UptimeEventPojo? =
        dsl.select(UPTIME_EVENT.asterisk())
            .from(UPTIME_EVENT)
            .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
            .and(UPTIME_EVENT.ENDED_AT.isNull)
            .fetchOneInto(UptimeEventPojo::class.java)

    fun endEventById(eventId: Int, endedAt: OffsetDateTime, configuration: Configuration? = jooqConfig) =
        DSL.using(configuration)
            .update(UPTIME_EVENT)
            .set(UPTIME_EVENT.ENDED_AT, endedAt)
            .set(UPTIME_EVENT.UPDATED_AT, endedAt)
            .where(UPTIME_EVENT.ID.eq(eventId))
            .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) =
        dsl.delete(UPTIME_EVENT)
            .where(UPTIME_EVENT.ENDED_AT.isNotNull)
            .and(UPTIME_EVENT.ENDED_AT.lessThan(limit))
            .execute()

    fun updateEventUpdatedAt(eventId: Int, updatedAt: OffsetDateTime) =
        dsl.update(UPTIME_EVENT)
            .set(UPTIME_EVENT.UPDATED_AT, updatedAt)
            .where(UPTIME_EVENT.ID.eq(eventId))
            .execute()

    fun isMonitorUp(monitorId: Int): Boolean =
        getPreviousEventByMonitorId(monitorId)?.let { it.status == UptimeStatus.UP } ?: false

    fun getEventsByMonitorId(monitorId: Int): List<UptimeEventDto> =
        dsl
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
