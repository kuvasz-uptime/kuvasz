package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DNS_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMetricsLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.percentileCont
import org.jooq.impl.DSL.round
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

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(DNS_METRICS_LOG)
        .where(DNS_METRICS_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<DnsMetricsLogDto> = dslContext
        .metricsLogDtoSelect(monitorId)
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(DNS_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(DNS_METRICS_LOG.CREATED_AT.desc(), DNS_METRICS_LOG.ID.desc())
        .fetchInto(DnsMetricsLogDto::class.java)

    fun fetchLastByMonitorId(monitorId: Long): DnsMetricsLogDto? = dslContext
        .metricsLogDtoSelect(monitorId)
        .orderBy(DNS_METRICS_LOG.CREATED_AT.desc(), DNS_METRICS_LOG.ID.desc())
        .limit(1)
        .fetchOneInto(DnsMetricsLogDto::class.java)

    fun getLatencyMetrics(monitorId: Long, period: Duration): LatencyMetricResult? {
        val thresholdSeconds = period.toSeconds()
        return dslContext
            .select(
                DNS_METRICS_LOG.MONITOR_ID.`as`(LatencyMetricResult::monitorId.name),
                round(avg(DNS_METRICS_LOG.LATENCY_MS)).cast(Int::class.java).`as`(LatencyMetricResult::avg.name),
                min(DNS_METRICS_LOG.LATENCY_MS).`as`(LatencyMetricResult::min.name),
                max(DNS_METRICS_LOG.LATENCY_MS).`as`(LatencyMetricResult::max.name),
                round(percentileCont(P90).withinGroupOrderBy(DNS_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p90.name),
                round(percentileCont(P95).withinGroupOrderBy(DNS_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p95.name),
                round(percentileCont(P99).withinGroupOrderBy(DNS_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p99.name),
            )
            .from(DNS_METRICS_LOG)
            .where(DNS_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(DNS_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            .and(DNS_METRICS_LOG.LATENCY_MS.isNotNull)
            .groupBy(DNS_METRICS_LOG.MONITOR_ID)
            .fetchOneInto(LatencyMetricResult::class.java)
    }

    private fun DSLContext.metricsLogDtoSelect(monitorId: Long) =
        select(
            DNS_METRICS_LOG.ID.`as`(DnsMetricsLogDto::id.name),
            DNS_METRICS_LOG.LATENCY_MS.`as`(DnsMetricsLogDto::latencyInMs.name),
            DNS_METRICS_LOG.CREATED_AT.`as`(DnsMetricsLogDto::createdAt.name),
        )
            .from(DNS_METRICS_LOG)
            .where(DNS_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
