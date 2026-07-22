package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class DnsMetricsLogRepository(private val dslContext: DSLContext) {

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

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(DNS_METRICS_LOG)
        .where(DNS_METRICS_LOG.CREATED_AT.lessThan(limit))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<DnsMetricsLogDto> = dslContext
        .select(
            DNS_METRICS_LOG.ID.`as`(DnsMetricsLogDto::id.name),
            DNS_METRICS_LOG.LATENCY_MS.`as`(DnsMetricsLogDto::latencyInMs.name),
            DNS_METRICS_LOG.CREATED_AT.`as`(DnsMetricsLogDto::createdAt.name),
        )
        .from(DNS_METRICS_LOG)
        .where(DNS_METRICS_LOG.MONITOR_ID.eq(monitorId))
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(DNS_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(DNS_METRICS_LOG.CREATED_AT.desc(), DNS_METRICS_LOG.ID.desc())
        .fetchInto(DnsMetricsLogDto::class.java)
}
