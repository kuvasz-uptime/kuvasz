package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.TCP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class TcpMetricsLogRepository(dslContext: DSLContext) :
    MonitorMetricsLogRepository<TcpMetricsLogRecord, TcpMetricsLogDto>(
        dslContext,
        MetricsLogTable(
            table = TCP_METRICS_LOG,
            id = TCP_METRICS_LOG.ID,
            monitorId = TCP_METRICS_LOG.MONITOR_ID,
            createdAt = TCP_METRICS_LOG.CREATED_AT,
            latency = TCP_METRICS_LOG.LATENCY_MS,
        ),
        TcpMetricsLogDto::class.java,
    ) {

    fun insertLog(monitorId: Long, latencyMs: Int?) {
        dslContext.insertInto(TCP_METRICS_LOG)
            .set(
                TcpMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setCreatedAt(getCurrentTimestamp())
            )
            .execute()
    }

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            TCP_METRICS_LOG.ID.`as`(TcpMetricsLogDto::id.name),
            TCP_METRICS_LOG.LATENCY_MS.`as`(TcpMetricsLogDto::latencyInMs.name),
            TCP_METRICS_LOG.CREATED_AT.`as`(TcpMetricsLogDto::createdAt.name),
        )
            .from(TCP_METRICS_LOG)
            .where(TCP_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
