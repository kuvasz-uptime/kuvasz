package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.HTTP_LATENCY_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpLatencyLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.OffsetDateTime

@Singleton
class HttpLatencyLogRepository(dslContext: DSLContext) :
    MonitorMetricsLogRepository<HttpLatencyLogRecord, LatencyLogDto>(
        dslContext,
        MetricsLogTable(
            table = HTTP_LATENCY_LOG,
            id = HTTP_LATENCY_LOG.ID,
            monitorId = HTTP_LATENCY_LOG.MONITOR_ID,
            createdAt = HTTP_LATENCY_LOG.CREATED_AT,
            latency = HTTP_LATENCY_LOG.LATENCY,
        ),
        LatencyLogDto::class.java,
    ) {

    fun insertLatencyForMonitor(monitorId: Long, latency: Int, createdAt: OffsetDateTime = getCurrentTimestamp()) {
        dslContext.insertInto(HTTP_LATENCY_LOG)
            .set(
                HttpLatencyLogRecord()
                    .setMonitorId(monitorId)
                    .setLatency(latency)
                    .setCreatedAt(createdAt)
            )
            .execute()
    }

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            HTTP_LATENCY_LOG.ID.`as`(LatencyLogDto::id.name),
            HTTP_LATENCY_LOG.LATENCY.`as`(LatencyLogDto::latencyInMs.name),
            HTTP_LATENCY_LOG.CREATED_AT.`as`(LatencyLogDto::createdAt.name)
        )
            .from(HTTP_LATENCY_LOG)
            .where(HTTP_LATENCY_LOG.MONITOR_ID.eq(monitorId))
}
