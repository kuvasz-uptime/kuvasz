package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Either
import arrow.core.Option
import arrow.core.toOption
import com.kuvaszuptime.kuvasz.models.DuplicationError
import com.kuvaszuptime.kuvasz.models.MonitorDuplicatedError
import com.kuvaszuptime.kuvasz.models.PersistenceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.MonitorDao
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toPersistenceError
import org.jooq.Configuration
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.inline
import org.jooq.impl.SQLDataType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorRepository @Inject constructor(jooqConfig: Configuration) : MonitorDao(jooqConfig) {

    private val dsl = jooqConfig.dsl()

    fun getMonitorDetails(monitorId: Int): Option<MonitorDetailsDto> =
        getMonitorDetailsSelect()
            .where(MONITOR.ID.eq(monitorId))
            .groupBy(
                MONITOR.ID,
                UPTIME_EVENT.STATUS,
                UPTIME_EVENT.STARTED_AT,
                UPTIME_EVENT.ERROR
            )
            .fetchOneInto(MonitorDetailsDto::class.java)
            .toOption()

    fun getMonitorsWithDetails(enabledOnly: Boolean): List<MonitorDetailsDto> =
        getMonitorDetailsSelect()
            .apply {
                if (enabledOnly) {
                    where(MONITOR.ENABLED.isTrue)
                }
            }
            .groupBy(
                MONITOR.ID,
                UPTIME_EVENT.STATUS,
                UPTIME_EVENT.STARTED_AT,
                UPTIME_EVENT.ERROR
            )
            .fetchInto(MonitorDetailsDto::class.java)

    fun getMonitors(enabledOnly: Boolean): List<MonitorPojo> =
        dsl
            .select(MONITOR.asterisk())
            .from(MONITOR)
            .apply {
                if (enabledOnly) {
                    where(MONITOR.ENABLED.isTrue)
                }
            }
            .fetchInto(MonitorPojo::class.java)

    fun returningInsert(monitorPojo: MonitorPojo): Either<PersistenceError, MonitorPojo> =
        try {
            Either.right(
                dsl
                    .insertInto(MONITOR)
                    .set(dsl.newRecord(MONITOR, monitorPojo))
                    .returning(MONITOR.asterisk())
                    .fetchOne()
                    .into(MonitorPojo::class.java)
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
                    .set(MONITOR.UPDATED_AT, getCurrentTimestamp())
                    .where(MONITOR.ID.eq(updatedPojo.id))
                    .returning(MONITOR.asterisk())
                    .fetchOne()
                    .into(MonitorPojo::class.java)
            )
        } catch (e: DataAccessException) {
            e.handle()
        }

    private fun getMonitorDetailsSelect() =
        dsl
            .select(
                MONITOR.ID.`as`("id"),
                MONITOR.NAME.`as`("name"),
                MONITOR.URL.`as`("url"),
                MONITOR.UPTIME_CHECK_INTERVAL.`as`("uptimeCheckInterval"),
                MONITOR.ENABLED.`as`("enabled"),
                MONITOR.CREATED_AT.`as`("createdAt"),
                MONITOR.UPDATED_AT.`as`("updatedAt"),
                UPTIME_EVENT.STATUS.`as`("uptimeStatus"),
                UPTIME_EVENT.STARTED_AT.`as`("uptimeStatusStartedAt"),
                UPTIME_EVENT.ERROR.`as`("uptimeError"),
                avg(LATENCY_LOG.LATENCY).`as`("averageLatencyInMs"),
                inline(null, SQLDataType.INTEGER).`as`("p95LatencyInMs"),
                inline(null, SQLDataType.INTEGER).`as`("p99LatencyInMs")
            )
            .from(MONITOR)
            .leftJoin(UPTIME_EVENT).on(MONITOR.ID.eq(UPTIME_EVENT.MONITOR_ID).and(UPTIME_EVENT.ENDED_AT.isNull))
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
