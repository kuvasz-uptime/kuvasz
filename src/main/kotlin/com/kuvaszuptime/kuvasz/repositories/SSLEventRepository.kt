package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.WithCertInfo
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

        if (event is WithCertInfo) {
            eventToInsert.sslExpiryDate = event.certInfo.validTo
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

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: SSLMonitorEvent) = dslContext
        .update(SSL_EVENT)
        .set(SSL_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is WithCertInfo) {
                set(SSL_EVENT.SSL_EXPIRY_DATE, newEvent.certInfo.validTo)
            }
        }
        .where(SSL_EVENT.ID.eq(eventId))
        .execute()

    fun fetchByMonitorId(monitorId: Long): List<SslEventRecord> = dslContext
        .selectFrom(SSL_EVENT)
        .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getEventsByMonitorId(monitorId: Long): List<SSLEventDto> = dslContext
        .select(
            SSL_EVENT.ID.`as`(SSLEventDto::id.name),
            SSL_EVENT.STATUS.`as`(SSLEventDto::status.name),
            SSL_EVENT.ERROR.`as`(SSLEventDto::error.name),
            SSL_EVENT.STARTED_AT.`as`(SSLEventDto::startedAt.name),
            SSL_EVENT.ENDED_AT.`as`(SSLEventDto::endedAt.name),
            SSL_EVENT.UPDATED_AT.`as`(SSLEventDto::updatedAt.name),
        )
        .from(SSL_EVENT)
        .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(SSL_EVENT.STARTED_AT.desc())
        .fetchInto(SSLEventDto::class.java)
}
