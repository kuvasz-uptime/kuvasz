package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.DnsUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.Duration
import java.time.OffsetDateTime

@Suppress("TooManyFunctions")
@Singleton
class DnsUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

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

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DnsUptimeEventDto> = dslContext
        .select(
            DNS_UPTIME_EVENT.ID.`as`(DnsUptimeEventDto::id.name),
            DNS_UPTIME_EVENT.STATUS.`as`(DnsUptimeEventDto::status.name),
            DNS_UPTIME_EVENT.ERROR.`as`(DnsUptimeEventDto::error.name),
            DNS_UPTIME_EVENT.STARTED_AT.`as`(DnsUptimeEventDto::startedAt.name),
            DNS_UPTIME_EVENT.ENDED_AT.`as`(DnsUptimeEventDto::endedAt.name),
            DNS_UPTIME_EVENT.UPDATED_AT.`as`(DnsUptimeEventDto::updatedAt.name),
        )
        .from(DNS_UPTIME_EVENT)
        .where(DNS_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(DNS_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) limit(limit)
        }
        .fetchInto(DnsUptimeEventDto::class.java)

    @Suppress("IgnoredReturnValue")
    override fun fetchAllInPeriod(period: Duration, monitorId: Long?): List<UptimeEventCalculationContext> {
        val periodStart = getCurrentTimestamp().minus(period)
        return dslContext
            .select(
                DNS_MONITOR.ID.`as`(UptimeEventCalculationContext::monitorId.name),
                DNS_MONITOR.ENABLED.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
                DNS_UPTIME_EVENT.STATUS.`as`(UptimeEventCalculationContext::status.name),
                DNS_UPTIME_EVENT.STARTED_AT.`as`(UptimeEventCalculationContext::startedAt.name),
                DNS_UPTIME_EVENT.ENDED_AT.`as`(UptimeEventCalculationContext::endedAt.name),
                DNS_UPTIME_EVENT.UPDATED_AT.`as`(UptimeEventCalculationContext::updatedAt.name),
            )
            .from(DNS_UPTIME_EVENT)
            .join(DNS_MONITOR).on(DNS_UPTIME_EVENT.MONITOR_ID.eq(DNS_MONITOR.ID))
            .where(DSL.coalesce(DNS_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            .apply {
                monitorId?.let { and(DNS_UPTIME_EVENT.MONITOR_ID.eq(it)) }
            }
            .fetchInto(UptimeEventCalculationContext::class.java)
    }

    override fun fetchLatestIncidentTimestamp(): OffsetDateTime? = dslContext
        .select(DSL.max(DSL.coalesce(DNS_UPTIME_EVENT.UPDATED_AT, DNS_UPTIME_EVENT.STARTED_AT)))
        .from(DNS_UPTIME_EVENT)
        .join(DNS_MONITOR).on(DNS_UPTIME_EVENT.MONITOR_ID.eq(DNS_MONITOR.ID))
        .where(DNS_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .and(DNS_MONITOR.ENABLED.isTrue)
        .fetchAny(0, OffsetDateTime::class.java)
}
