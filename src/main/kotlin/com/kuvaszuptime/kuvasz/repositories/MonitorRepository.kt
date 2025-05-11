package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import com.kuvaszuptime.kuvasz.Keys.UNIQUE_MONITOR_NAME
import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
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
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.`when`

@Singleton
@Suppress("TooManyFunctions")
class MonitorRepository(private val dslContext: DSLContext) {

    fun findById(monitorId: Long, ctx: DSLContext = dslContext): MonitorRecord? = ctx
        .selectFrom(MONITOR)
        .where(MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): MonitorRecord? = dslContext
        .selectFrom(MONITOR)
        .where(MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<MonitorRecord> = dslContext
        .selectFrom(MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<MonitorRecord> = dslContext
        .selectFrom(MONITOR)
        .where(MONITOR.ENABLED.eq(enabled))
        .fetch()

    fun deleteById(monitorId: Long): Int = dslContext
        .deleteFrom(MONITOR)
        .where(MONITOR.ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun getMonitorsWithDetails(enabledOnly: Boolean, sortedBy: SortField<*>? = null): List<MonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                if (enabledOnly) {
                    where(MONITOR.ENABLED.isTrue)
                }
                if (sortedBy != null) {
                    orderBy(sortedBy)
                }
            }
            .fetchInto(MonitorDetailsDto::class.java)

    fun getMonitorWithDetails(monitorId: Long): MonitorDetailsDto? =
        monitorDetailsSelect()
            .where(MONITOR.ID.eq(monitorId))
            .fetchOneInto(MonitorDetailsDto::class.java)

    fun returningInsert(monitor: MonitorRecord): Either<PersistenceException, MonitorRecord> =
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
    ): Either<PersistenceException, MonitorRecord> =
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

    /**
     * Inserts a new monitor or updates an existing one if the name already exists.
     */
    fun upsert(monitor: MonitorRecord): MonitorRecord {
        return dslContext.transactionResult { config ->
            val ctx = config.dsl()
            ctx.insertInto(MONITOR)
                .set(monitor)
                .onConflictOnConstraint(UNIQUE_MONITOR_NAME)
                .doUpdate()
                .setNonKeyToExcluded()
                .set(MONITOR.UPDATED_AT, getCurrentTimestamp())
                .returning(MONITOR.asterisk())
                .fetchOneOrThrow()
        }
    }

    /**
     * Deletes all monitors except the ones with the given IDs.
     */
    fun deleteAllExcept(ignoredIds: List<Long>): Int = dslContext
        .deleteFrom(MONITOR)
        .where(MONITOR.ID.notIn(ignoredIds))
        .execute()

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

    /**
     * Converts a DataAccessException to a PersistenceException by matching duplication errors.
     */
    private fun DataAccessException.handle(): Either<PersistenceException, Nothing> {
        val persistenceError = toPersistenceError()
        return Either.Left(
            if (persistenceError is DuplicationException) {
                MonitorDuplicatedException()
            } else {
                persistenceError
            }
        )
    }
}
