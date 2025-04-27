package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceError
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType
import java.math.BigDecimal

@Singleton
class MonitorRepository(private val dslContext: DSLContext) {

    private val detailsGroupByFields = listOf(
        MONITOR.ID,
        UPTIME_EVENT.STATUS,
        UPTIME_EVENT.STARTED_AT,
        UPTIME_EVENT.UPDATED_AT,
        UPTIME_EVENT.ERROR,
        SSL_EVENT.STATUS,
        SSL_EVENT.STARTED_AT,
        SSL_EVENT.UPDATED_AT,
        SSL_EVENT.ERROR
    )

    fun findById(monitorId: Int, ctx: DSLContext = dslContext): MonitorRecord? = ctx
        .selectFrom(MONITOR)
        .where(MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): MonitorRecord? = dslContext
        .selectFrom(MONITOR)
        .where(MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchByEnabled(enabled: Boolean): List<MonitorRecord> = dslContext
        .selectFrom(MONITOR)
        .where(MONITOR.ENABLED.eq(enabled))
        .fetch()

    fun deleteById(monitorId: Int): Int = dslContext
        .deleteFrom(MONITOR)
        .where(MONITOR.ID.eq(monitorId))
        .execute()

    fun getMonitorsWithDetails(enabledOnly: Boolean): List<MonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                if (enabledOnly) {
                    @Suppress("IgnoredReturnValue")
                    where(MONITOR.ENABLED.isTrue)
                }
            }
            .groupBy(detailsGroupByFields)
            .fetchInto(MonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Int): MonitorDetailsDto? =
        monitorDetailsSelect()
            .where(MONITOR.ID.eq(monitorId))
            .groupBy(detailsGroupByFields)
            .fetchOneInto(MonitorDetailsDto::class.java)

    fun returningInsert(monitor: MonitorRecord): Either<PersistenceError, MonitorRecord> =
        try {
            Either.Right(
                dslContext
                    .insertInto(MONITOR)
                    .set(monitor)
                    .returning(MONITOR.asterisk())
                    .fetchOneOrThrow<MonitorRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    fun returningUpdate(
        updatedMonitor: MonitorRecord,
        txCtx: DSLContext = dslContext,
    ): Either<PersistenceError, MonitorRecord> =
        try {
            Either.Right(
                txCtx
                    .update(MONITOR)
                    .set(MONITOR.NAME, updatedMonitor.name)
                    .set(MONITOR.URL, updatedMonitor.url)
                    .set(MONITOR.UPTIME_CHECK_INTERVAL, updatedMonitor.uptimeCheckInterval)
                    .set(MONITOR.ENABLED, updatedMonitor.enabled)
                    .set(MONITOR.SSL_CHECK_ENABLED, updatedMonitor.sslCheckEnabled)
                    .set(MONITOR.UPDATED_AT, getCurrentTimestamp())
                    .set(MONITOR.PAGERDUTY_INTEGRATION_KEY, updatedMonitor.pagerdutyIntegrationKey)
                    .set(MONITOR.REQUEST_METHOD, updatedMonitor.requestMethod)
                    .set(MONITOR.FOLLOW_REDIRECTS, updatedMonitor.followRedirects)
                    .set(MONITOR.LATENCY_HISTORY_ENABLED, updatedMonitor.latencyHistoryEnabled)
                    .set(MONITOR.FORCE_NO_CACHE, updatedMonitor.forceNoCache)
                    .where(MONITOR.ID.eq(updatedMonitor.id))
                    .returning(MONITOR.asterisk())
                    .fetchOneOrThrow<MonitorRecord>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    private fun monitorDetailsSelect() = dslContext
        .select(
            MONITOR.ID.`as`(MonitorDetailsDto::id.name),
            MONITOR.NAME.`as`(MonitorDetailsDto::name.name),
            MONITOR.URL.`as`(MonitorDetailsDto::url.name),
            MONITOR.UPTIME_CHECK_INTERVAL.`as`(MonitorDetailsDto::uptimeCheckInterval.name),
            MONITOR.ENABLED.`as`(MonitorDetailsDto::enabled.name),
            MONITOR.SSL_CHECK_ENABLED.`as`(MonitorDetailsDto::sslCheckEnabled.name),
            MONITOR.CREATED_AT.`as`(MonitorDetailsDto::createdAt.name),
            MONITOR.UPDATED_AT.`as`(MonitorDetailsDto::updatedAt.name),
            UPTIME_EVENT.STATUS.`as`(MonitorDetailsDto::uptimeStatus.name),
            UPTIME_EVENT.STARTED_AT.`as`(MonitorDetailsDto::uptimeStatusStartedAt.name),
            UPTIME_EVENT.UPDATED_AT.`as`(MonitorDetailsDto::lastUptimeCheck.name),
            SSL_EVENT.STATUS.`as`(MonitorDetailsDto::sslStatus.name),
            SSL_EVENT.STARTED_AT.`as`(MonitorDetailsDto::sslStatusStartedAt.name),
            SSL_EVENT.UPDATED_AT.`as`(MonitorDetailsDto::lastSSLCheck.name),
            UPTIME_EVENT.ERROR.`as`(MonitorDetailsDto::uptimeError.name),
            SSL_EVENT.ERROR.`as`(MonitorDetailsDto::sslError.name),
            `when`(MONITOR.LATENCY_HISTORY_ENABLED.isTrue, round(avg(LATENCY_LOG.LATENCY), -1))
                .otherwise(inline(null, BigDecimal::class.java))
                .`as`(MonitorDetailsDto::averageLatencyInMs.name),
            // p95 and p99 latency are added later, they're always null here
            inline(null, SQLDataType.INTEGER).`as`(MonitorDetailsDto::p95LatencyInMs.name),
            inline(null, SQLDataType.INTEGER).`as`(MonitorDetailsDto::p99LatencyInMs.name),
            `when`(
                MONITOR.PAGERDUTY_INTEGRATION_KEY.isNull.or(MONITOR.PAGERDUTY_INTEGRATION_KEY.eq("")),
                StringUtils.FALSE
            ).otherwise(StringUtils.TRUE).`as`(MonitorDetailsDto::pagerdutyKeyPresent.name),
            MONITOR.LATENCY_HISTORY_ENABLED.`as`(MonitorDetailsDto::latencyHistoryEnabled.name),
            MONITOR.FORCE_NO_CACHE.`as`(MonitorDetailsDto::forceNoCache.name),
            MONITOR.FOLLOW_REDIRECTS.`as`(MonitorDetailsDto::followRedirects.name),
            MONITOR.REQUEST_METHOD.`as`(MonitorDetailsDto::requestMethod.name)
        )
        .from(MONITOR)
        .leftJoin(UPTIME_EVENT).on(MONITOR.ID.eq(UPTIME_EVENT.MONITOR_ID).and(UPTIME_EVENT.ENDED_AT.isNull))
        .leftJoin(SSL_EVENT).on(MONITOR.ID.eq(SSL_EVENT.MONITOR_ID).and(SSL_EVENT.ENDED_AT.isNull))
        .leftJoin(LATENCY_LOG).on(MONITOR.ID.eq(LATENCY_LOG.MONITOR_ID))

    private fun DataAccessException.handle(): Either<PersistenceError, Nothing> {
        val persistenceError = toPersistenceError()
        return Either.Left(
            if (persistenceError is DuplicationError) {
                MonitorDuplicatedError()
            } else persistenceError
        )
    }
}
