package com.kuvaszuptime.kuvasz.repositories

import arrow.core.toOption
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.SslEventDao
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import com.kuvaszuptime.kuvasz.util.toSSLStatus
import org.jooq.Configuration
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SSLEventRepository @Inject constructor(jooqConfig: Configuration) : SslEventDao(jooqConfig) {
    private val dsl = jooqConfig.dsl()

    fun insertFromMonitorEvent(event: SSLMonitorEvent) {
        val eventToInsert = SslEventPojo()
            .setMonitorId(event.monitor.id)
            .setStatus(event.toSSLStatus())
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is SSLInvalidEvent) {
            eventToInsert.error = event.error.message
        }

        insert(eventToInsert)
    }

    fun getPreviousEventByMonitorId(monitorId: Int) =
        dsl.select(SSL_EVENT.asterisk())
            .from(SSL_EVENT)
            .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
            .and(SSL_EVENT.ENDED_AT.isNull)
            .fetchOneInto(SslEventPojo::class.java)
            .toOption()

    fun endEventById(eventId: Int, endedAt: OffsetDateTime) =
        dsl.update(SSL_EVENT)
            .set(SSL_EVENT.ENDED_AT, endedAt)
            .set(SSL_EVENT.UPDATED_AT, endedAt)
            .where(SSL_EVENT.ID.eq(eventId))
            .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) =
        dsl.delete(SSL_EVENT)
            .where(SSL_EVENT.ENDED_AT.isNotNull)
            .and(SSL_EVENT.ENDED_AT.lessThan(limit))
            .execute()

    fun updateEventUpdatedAt(eventId: Int, updatedAt: OffsetDateTime) =
        dsl.update(SSL_EVENT)
            .set(SSL_EVENT.UPDATED_AT, updatedAt)
            .where(SSL_EVENT.ID.eq(eventId))
            .execute()
}
