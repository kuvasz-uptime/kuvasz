package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DockerMonitor.DOCKER_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.DockerUptimeEvent.DOCKER_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.event.DockerUptimeEventDto
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerUptimeMonitorEvent
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
class DockerUptimeEventRepository(private val dslContext: DSLContext) : UptimeEventRepository {

    private fun DockerMonitorDownEvent.getPersistableError() = toStructuredMessage().error

    fun insertFromMonitorEvent(
        event: DockerUptimeMonitorEvent,
        ctx: DSLContext? = dslContext,
    ): DockerUptimeEventRecord {
        val eventToInsert = DockerUptimeEventRecord()
            .setMonitorId(event.monitor.id)
            .setStatus(event.uptimeStatus)
            .setStartedAt(event.dispatchedAt)
            .setUpdatedAt(event.dispatchedAt)

        if (event is DockerMonitorDownEvent) {
            eventToInsert.error = event.getPersistableError()
        }

        return (ctx ?: dslContext).insertInto(DOCKER_UPTIME_EVENT)
            .set(eventToInsert)
            .returning(DOCKER_UPTIME_EVENT.asterisk())
            .fetchOneOrThrow<DockerUptimeEventRecord>()
    }

    fun fetchByMonitorId(monitorId: Long): List<DockerUptimeEventRecord> = dslContext
        .selectFrom(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .fetch()

    fun getPreviousEventByMonitorId(monitorId: Long): DockerUptimeEventRecord? =
        dslContext.transactionResult { config ->
            val txCtx = config.dsl()
            val uptimeRecords = txCtx
                .selectFrom(DOCKER_UPTIME_EVENT)
                .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
                .and(DOCKER_UPTIME_EVENT.ENDED_AT.isNull)
                .fetch()

            if (uptimeRecords.size <= 1) return@transactionResult uptimeRecords.firstOrNull()

            uptimeRecords.dropLast(1).map { it.id }.let { conflictingEventIds ->
                txCtx.deleteFrom(DOCKER_UPTIME_EVENT)
                    .where(DOCKER_UPTIME_EVENT.ID.`in`(conflictingEventIds))
                    .execute()
            }

            uptimeRecords.last()
        }

    fun endEventById(eventId: Long, endedAt: OffsetDateTime, ctx: DSLContext = dslContext) = ctx
        .update(DOCKER_UPTIME_EVENT)
        .set(DOCKER_UPTIME_EVENT.ENDED_AT, endedAt)
        .set(DOCKER_UPTIME_EVENT.UPDATED_AT, endedAt)
        .where(DOCKER_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    fun deleteEventsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.ENDED_AT.isNotNull)
        .and(DOCKER_UPTIME_EVENT.ENDED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun updateEvent(eventId: Long, newEvent: DockerUptimeMonitorEvent) = dslContext
        .update(DOCKER_UPTIME_EVENT)
        .set(DOCKER_UPTIME_EVENT.UPDATED_AT, newEvent.dispatchedAt)
        .apply {
            if (newEvent is DockerMonitorDownEvent) {
                set(DOCKER_UPTIME_EVENT.ERROR, newEvent.getPersistableError())
            }
        }
        .where(DOCKER_UPTIME_EVENT.ID.eq(eventId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DockerUptimeEventDto> = dslContext
        .select(
            DOCKER_UPTIME_EVENT.ID.`as`(DockerUptimeEventDto::id.name),
            DOCKER_UPTIME_EVENT.STATUS.`as`(DockerUptimeEventDto::status.name),
            DOCKER_UPTIME_EVENT.ERROR.`as`(DockerUptimeEventDto::error.name),
            DOCKER_UPTIME_EVENT.STARTED_AT.`as`(DockerUptimeEventDto::startedAt.name),
            DOCKER_UPTIME_EVENT.ENDED_AT.`as`(DockerUptimeEventDto::endedAt.name),
            DOCKER_UPTIME_EVENT.UPDATED_AT.`as`(DockerUptimeEventDto::updatedAt.name),
        )
        .from(DOCKER_UPTIME_EVENT)
        .where(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(monitorId))
        .orderBy(DOCKER_UPTIME_EVENT.STARTED_AT.desc())
        .apply {
            if (limit != null) limit(limit)
        }
        .fetchInto(DockerUptimeEventDto::class.java)

    @Suppress("IgnoredReturnValue")
    override fun fetchAllInPeriod(
        period: Duration,
        monitorIds: List<Long>?,
    ): List<UptimeEventCalculationContext> {
        val periodStart = getCurrentTimestamp().minus(period)
        return dslContext
            .select(
                DOCKER_MONITOR.ID.`as`(UptimeEventCalculationContext::monitorId.name),
                DOCKER_MONITOR.ENABLED.`as`(UptimeEventCalculationContext::isMonitorEnabled.name),
                DOCKER_UPTIME_EVENT.STATUS.`as`(UptimeEventCalculationContext::status.name),
                DOCKER_UPTIME_EVENT.STARTED_AT.`as`(UptimeEventCalculationContext::startedAt.name),
                DOCKER_UPTIME_EVENT.ENDED_AT.`as`(UptimeEventCalculationContext::endedAt.name),
                DOCKER_UPTIME_EVENT.UPDATED_AT.`as`(UptimeEventCalculationContext::updatedAt.name),
            )
            .from(DOCKER_UPTIME_EVENT)
            .join(DOCKER_MONITOR).on(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(DOCKER_MONITOR.ID))
            .where(DSL.coalesce(DOCKER_UPTIME_EVENT.ENDED_AT, DSL.now()).greaterThan(periodStart))
            .apply {
                monitorIds?.let { and(DOCKER_UPTIME_EVENT.MONITOR_ID.`in`(it)) }
            }
            .fetchInto(UptimeEventCalculationContext::class.java)
    }

    override fun fetchLatestIncidentTimestamp(): OffsetDateTime? = dslContext
        .select(DSL.max(DSL.coalesce(DOCKER_UPTIME_EVENT.UPDATED_AT, DOCKER_UPTIME_EVENT.STARTED_AT)))
        .from(DOCKER_UPTIME_EVENT)
        .join(DOCKER_MONITOR).on(DOCKER_UPTIME_EVENT.MONITOR_ID.eq(DOCKER_MONITOR.ID))
        .where(DOCKER_UPTIME_EVENT.STATUS.eq(UptimeStatus.DOWN))
        .and(DOCKER_MONITOR.ENABLED.isTrue)
        .fetchAny(0, OffsetDateTime::class.java)
}
