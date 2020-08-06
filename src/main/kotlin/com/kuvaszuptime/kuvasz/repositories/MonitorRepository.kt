package com.kuvaszuptime.kuvasz.repositories

import arrow.core.Option
import arrow.core.toOption
import com.kuvaszuptime.kuvasz.models.MonitorDetails
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.tables.UptimeEvent.UPTIME_EVENT
import com.kuvaszuptime.kuvasz.tables.daos.MonitorDao
import org.jooq.Configuration
import org.jooq.impl.DSL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorRepository @Inject constructor(jooqConfig: Configuration) : MonitorDao(jooqConfig) {

    private val dsl = jooqConfig.dsl()

    fun getMonitorDetails(monitorId: Int): Option<MonitorDetails> =
        getMonitorDetailsSelect()
            .where(MONITOR.ID.eq(monitorId))
            .groupBy(
                MONITOR.ID,
                UPTIME_EVENT.STATUS,
                UPTIME_EVENT.STARTED_AT,
                UPTIME_EVENT.ERROR
            )
            .fetchOneInto(MonitorDetails::class.java)
            .toOption()

    fun getMonitorDetails(enabledOnly: Boolean): List<MonitorDetails> =
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
            .fetchInto(MonitorDetails::class.java)

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
                DSL.avg(LATENCY_LOG.LATENCY).`as`("averageLatencyInMs")
            )
            .from(MONITOR)
            .leftJoin(UPTIME_EVENT).on(MONITOR.ID.eq(UPTIME_EVENT.MONITOR_ID).and(UPTIME_EVENT.ENDED_AT.isNull))
            .leftJoin(LATENCY_LOG).on(MONITOR.ID.eq(LATENCY_LOG.MONITOR_ID))
}
