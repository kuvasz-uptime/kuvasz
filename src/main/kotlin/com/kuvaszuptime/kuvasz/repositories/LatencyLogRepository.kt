package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.daos.LatencyLogDao
import com.kuvaszuptime.kuvasz.tables.pojos.LatencyLogPojo
import org.jooq.Configuration
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LatencyLogRepository @Inject constructor(jooqConfig: Configuration) : LatencyLogDao(jooqConfig) {
    private val dsl = jooqConfig.dsl()

    fun insertLatencyForMonitor(monitorId: Int, latency: Int) {
        insert(
            LatencyLogPojo()
                .setMonitorId(monitorId)
                .setLatency(latency)
        )
    }

    fun deleteLogsBeforeDate(limit: OffsetDateTime) =
        dsl.delete(LATENCY_LOG)
            .where(LATENCY_LOG.CREATED_AT.lessThan(limit))
            .execute()

    fun getLatencyPercentileForMonitor(monitorId: Int, percentile: Int): Int? =
        dsl.fetch(
            """
            with ranked as (
                select latency_log.latency, ntile(100) over (order by latency_log.latency) as quartile 
                from latency_log where monitor_id = ? 
            )
            select min(latency) from ranked
            where quartile = ?
            group by quartile""", monitorId, percentile
        ).into(Int::class.java).firstOrNull()
}
