package com.kuvaszuptime.kuvasz.repositories

import arrow.core.toOption
import com.kuvaszuptime.kuvasz.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.events.toUptimeStatus
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.UptimeEventDao
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import org.jooq.Configuration
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UptimeEventRepository @Inject constructor(jooqConfig: Configuration) : UptimeEventDao(jooqConfig) {
    private val dsl = jooqConfig.dsl()

    fun insertFromMonitorEvent(event: UptimeMonitorEvent) {
        val eventToInsert = UptimeEventPojo()
            .setMonitorId(event.monitor.id)
            .setStatus(event.toUptimeStatus())
            .setStartedAt(event.dispatchedAt)

        if (event is MonitorDownEvent) {
            eventToInsert.error = event.error.message
        }

        insert(eventToInsert)
    }

    fun getPreviousEventByMonitorId(monitorId: Int) =
        dsl.select(UPTIME_EVENT.asterisk())
            .from(UPTIME_EVENT)
            .where(UPTIME_EVENT.MONITOR_ID.eq(monitorId))
            .and(UPTIME_EVENT.ENDED_AT.isNull)
            .fetchOneInto(UptimeEventPojo::class.java)
            .toOption()

    fun endEventById(eventId: Int, endedAt: OffsetDateTime) =
        dsl.update(UPTIME_EVENT)
            .set(UPTIME_EVENT.ENDED_AT, endedAt)
            .where(UPTIME_EVENT.ID.eq(eventId))
            .execute()
}
