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
import com.kuvaszuptime.kuvasz.tables.daos.MonitorDao
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.util.fetchOneIntoOrThrow
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceError
import org.jooq.Configuration
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.`when`
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.round
import org.jooq.impl.SQLDataType
import javax.inject.Singleton

@Singleton
class MonitorRepository(jooqConfig: Configuration) : MonitorDao(jooqConfig) {

    private val dsl = jooqConfig.dsl()

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

    fun getMonitorsWithDetails(enabledOnly: Boolean): List<MonitorDetailsDto> =
        monitorDetailsSelect()
            .apply {
                if (enabledOnly) {
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

    fun returningInsert(monitorPojo: MonitorPojo): Either<PersistenceError, MonitorPojo> =
        try {
            Either.right(
                dsl
                    .insertInto(MONITOR)
                    .set(dsl.newRecord(MONITOR, monitorPojo))
                    .returning(MONITOR.asterisk())
                    .fetchOneIntoOrThrow<MonitorRecord, MonitorPojo>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    fun returningUpdate(updatedPojo: MonitorPojo): Either<PersistenceError, MonitorPojo> =
        try {
            Either.right(
                dsl
                    .update(MONITOR)
                    .set(MONITOR.NAME, updatedPojo.name)
                    .set(MONITOR.URL, updatedPojo.url)
                    .set(MONITOR.UPTIME_CHECK_INTERVAL, updatedPojo.uptimeCheckInterval)
                    .set(MONITOR.ENABLED, updatedPojo.enabled)
                    .set(MONITOR.SSL_CHECK_ENABLED, updatedPojo.sslCheckEnabled)
                    .set(MONITOR.UPDATED_AT, getCurrentTimestamp())
                    .set(MONITOR.PAGERDUTY_INTEGRATION_KEY, updatedPojo.pagerdutyIntegrationKey)
                    .where(MONITOR.ID.eq(updatedPojo.id))
                    .returning(MONITOR.asterisk())
                    .fetchOneIntoOrThrow<MonitorRecord, MonitorPojo>()
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    private fun monitorDetailsSelect() =
        dsl
            .select(
                MONITOR.ID.`as`("id"),
                MONITOR.NAME.`as`("name"),
                MONITOR.URL.`as`("url"),
                MONITOR.UPTIME_CHECK_INTERVAL.`as`("uptimeCheckInterval"),
                MONITOR.ENABLED.`as`("enabled"),
                MONITOR.SSL_CHECK_ENABLED.`as`("sslCheckEnabled"),
                MONITOR.CREATED_AT.`as`("createdAt"),
                MONITOR.UPDATED_AT.`as`("updatedAt"),
                UPTIME_EVENT.STATUS.`as`("uptimeStatus"),
                UPTIME_EVENT.STARTED_AT.`as`("uptimeStatusStartedAt"),
                UPTIME_EVENT.UPDATED_AT.`as`("lastUptimeCheck"),
                SSL_EVENT.STATUS.`as`("sslStatus"),
                SSL_EVENT.STARTED_AT.`as`("sslStatusStartedAt"),
                SSL_EVENT.UPDATED_AT.`as`("lastSSLCheck"),
                UPTIME_EVENT.ERROR.`as`("uptimeError"),
                SSL_EVENT.ERROR.`as`("sslError"),
                round(avg(LATENCY_LOG.LATENCY), -1).`as`("averageLatencyInMs"),
                inline(null, SQLDataType.INTEGER).`as`("p95LatencyInMs"),
                inline(null, SQLDataType.INTEGER).`as`("p99LatencyInMs"),
                `when`(
                    MONITOR.PAGERDUTY_INTEGRATION_KEY.isNull.or(MONITOR.PAGERDUTY_INTEGRATION_KEY.eq("")),
                    "FALSE"
                ).otherwise("TRUE").`as`("pagerdutyKeyPresent")
            )
            .from(MONITOR)
            .leftJoin(UPTIME_EVENT).on(MONITOR.ID.eq(UPTIME_EVENT.MONITOR_ID).and(UPTIME_EVENT.ENDED_AT.isNull))
            .leftJoin(SSL_EVENT).on(MONITOR.ID.eq(SSL_EVENT.MONITOR_ID).and(SSL_EVENT.ENDED_AT.isNull))
            .leftJoin(LATENCY_LOG).on(MONITOR.ID.eq(LATENCY_LOG.MONITOR_ID))

    private fun DataAccessException.handle(): Either<PersistenceError, Nothing> {
        val persistenceError = toPersistenceError()
        return Either.left(
            if (persistenceError is DuplicationError) {
                MonitorDuplicatedError()
            } else persistenceError
        )
    }
}
