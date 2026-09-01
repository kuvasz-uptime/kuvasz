package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class DnsMetricsLogRepository(dslContext: DSLContext) :
    MonitorMetricsLogRepository<DnsMetricsLogRecord, DnsMetricsLogDto>(
        dslContext,
        MetricsLogTable(
            table = DNS_METRICS_LOG,
            id = DNS_METRICS_LOG.ID,
            monitorId = DNS_METRICS_LOG.MONITOR_ID,
            createdAt = DNS_METRICS_LOG.CREATED_AT,
            latency = DNS_METRICS_LOG.LATENCY_MS,
        ),
        DnsMetricsLogDto::class.java,
    ) {

    fun insertLog(monitorId: Long, latencyMs: Int?) {
        dslContext.insertInto(DNS_METRICS_LOG)
            .set(
                DnsMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setCreatedAt(getCurrentTimestamp())
            )
            .execute()
    }

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            DNS_METRICS_LOG.ID.`as`(DnsMetricsLogDto::id.name),
            DNS_METRICS_LOG.LATENCY_MS.`as`(DnsMetricsLogDto::latencyInMs.name),
            DNS_METRICS_LOG.CREATED_AT.`as`(DnsMetricsLogDto::createdAt.name),
        )
            .from(DNS_METRICS_LOG)
            .where(DNS_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
