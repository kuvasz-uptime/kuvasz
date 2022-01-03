package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.SslEventDao
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import jakarta.inject.Singleton
import org.jooq.Configuration
import org.jooq.impl.DSL
import java.time.OffsetDateTime

@Singleton
class SSLEventRepository(private val jooqConfig: Configuration) : SslEventDao(jooqConfig) {
    private val dsl = jooqConfig.dsl()

    fun insertFromMonitorEvent(event: SSLMonitorEvent, configuration: Configuration? = jooqConfig) {
        val eventToInsert = SslEventPojo()
            .setMonitorId(event.monitor.id)
            .setStatus(event.sslStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is SSLInvalidEvent) {
            eventToInsert.error = event.error.message
        }

        DSL.using(configuration)
            .insertInto(SSL_EVENT)
            .set(dsl.newRecord(SSL_EVENT, eventToInsert))
            .execute()
    }

    fun getPreviousEventByMonitorId(monitorId: Int): SslEventPojo? =
        dsl.select(SSL_EVENT.asterisk())
            .from(SSL_EVENT)
            .where(SSL_EVENT.MONITOR_ID.eq(monitorId))
            .and(SSL_EVENT.ENDED_AT.isNull)
            .fetchOneInto(SslEventPojo::class.java)

    fun endEventById(eventId: Int, endedAt: OffsetDateTime, configuration: Configuration? = jooqConfig) =
        DSL.using(configuration)
            .update(SSL_EVENT)
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

    fun getEventsByMonitorId(monitorId: Int): List<SSLEventDto> =
        dsl
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
