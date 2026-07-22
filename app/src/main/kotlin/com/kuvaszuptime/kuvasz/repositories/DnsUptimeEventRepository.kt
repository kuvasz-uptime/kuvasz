package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Singleton
class DnsUptimeEventRepository(private val dslContext: DSLContext) {

    private fun DnsMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(event: DnsUptimeMonitorEvent, ctx: DSLContext? = dslContext): DnsUptimeEventRecord {
        val eventToInsert = DnsUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is DnsMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(DNS_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(DNS_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<DnsUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<DnsUptimeEventRecord> = dslContext
        .selectFrom(DNS_UPTIME_EVENT)
        .where(DNS_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): DnsUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx
                .selectFrom(DNS_UPTIME_EVENT)
                .where(DNS_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(DNS_UPTIME_EVENT.ENDED_AT.isNull)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(DNS_UPTIME_EVENT)
                    .where(DNS_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(DNS_UPTIME_EVENT)
        .set(DNS_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(DNS_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(DNS_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(DNS_UPTIME_EVENT)
        .where(DNS_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(DNS_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: DnsUptimeMonitorEvent) = dslContext
        .update(DNS_UPTIME_EVENT)
        .set(DNS_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is DnsMonitorDownEvent) {
                set(DNS_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(DNS_UPTIME_EVENT.ID.eq(eventId))
        .execute()
}
