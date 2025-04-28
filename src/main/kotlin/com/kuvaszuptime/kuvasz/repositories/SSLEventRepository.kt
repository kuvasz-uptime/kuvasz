package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.records.SslEventRecord
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Singleton
class SSLEventRepository(private val dslContext: DSLContext) {

    fun insertFromMonitorEvent(event: SSLMonitorEvent, ctx: DSLContext = dslContext) {
        val eventToInsert = SslEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.sslStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is SSLInvalidEvent) {
            eventToInsert.error = event.error.message
        }

        ctx.insertInto(SSL_EVENT)
            .set(eventToInsert)
            .execute()
    }

    fun getPreviousEventByMonitorId(monitorId: Long): SslEventRecord? = dslContext
        .selectFrom(SSL_EVENT)
        .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
        .and(SSL_EVENT.ENDED_AT.isNull)
        .fetchOne()

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(SSL_EVENT)
        .set(SSL_EVENT.ENDED_AT, endedAt)
        .set(SSL_EVENT.UPDATED_AT, endedAt)
        .where(SSL_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(SSL_EVENT)
        .where(SSL_EVENT.ENDED_AT.isNotNull)
        .and(SSL_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    fun updateEventUpdatedAt(eventId: Long, updatedAt: OffsetDateTime) = dslContext
        .update(SSL_EVENT)
        .set(SSL_EVENT.UPDATED_AT, updatedAt)
        .where(SSL_EVENT.ID.eq(eventId))
        .execute()

    fun fetchByMonitorId(monitorId: Long): List<SslEventRecord> = dslContext
        .selectFrom(SSL_EVENT)
        .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getEventsByMonitorId(monitorId: Long): List<SSLEventDto> = dslContext
        .select(
            SSL_EVENT.STATUS.`as`("status"),
            SSL_EVENT.ERROR.`as`("error"),
            SSL_EVENT.STARTED_AT.`as`("startedAt"),
            SSL_EVENT.ENDED_AT.`as`("endedAt"),
            SSL_EVENT.UPDATED_AT.`as`("updatedAt")
        )
        .from(SSL_EVENT)
        .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(SSL_EVENT.STARTED_AT.desc())
        .fetchInto(SSLEventDto::class.java)
}
