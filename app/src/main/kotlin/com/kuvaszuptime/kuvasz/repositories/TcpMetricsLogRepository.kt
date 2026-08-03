package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.TCP_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMetricsLogDto
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
class TcpMetricsLogRepository(private val dslContext: DSLContext) {

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

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<TcpMetricsLogDto> = dslContext
        .metricsLogDtoSelect(monitorId)
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(TCP_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(TCP_METRICS_LOG.CREATED_AT.desc(), TCP_METRICS_LOG.ID.desc())
        .fetchInto(TcpMetricsLogDto::class.java)

    fun getLatencyMetrics(monitorId: Long, period: Duration): LatencyMetricResult? {
        val thresholdSeconds = period.toSeconds()
        return dslContext
            .select(
                TCP_METRICS_LOG.MONITOR_ID.`as`(LatencyMetricResult::monitorId.name),
                round(avg(TCP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java).`as`(LatencyMetricResult::avg.name),
                min(TCP_METRICS_LOG.LATENCY_MS).`as`(LatencyMetricResult::min.name),
                max(TCP_METRICS_LOG.LATENCY_MS).`as`(LatencyMetricResult::max.name),
                round(percentileCont(P90).withinGroupOrderBy(TCP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p90.name),
                round(percentileCont(P95).withinGroupOrderBy(TCP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p95.name),
                round(percentileCont(P99).withinGroupOrderBy(TCP_METRICS_LOG.LATENCY_MS)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p99.name),
            )
            .from(TCP_METRICS_LOG)
            .where(TCP_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(TCP_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            .and(TCP_METRICS_LOG.LATENCY_MS.isNotNull)
            .groupBy(TCP_METRICS_LOG.MONITOR_ID)
            .fetchOneInto(LatencyMetricResult::class.java)
    }

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(TCP_METRICS_LOG)
        .where(TCP_METRICS_LOG.CREATED_AT.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(TCP_METRICS_LOG)
        .where(TCP_METRICS_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    private fun DSLContext.metricsLogDtoSelect(monitorId: Long) =
        select(
            TCP_METRICS_LOG.ID.`as`(TcpMetricsLogDto::id.name),
            TCP_METRICS_LOG.LATENCY_MS.`as`(TcpMetricsLogDto::latencyInMs.name),
            TCP_METRICS_LOG.CREATED_AT.`as`(TcpMetricsLogDto::createdAt.name),
        )
            .from(TCP_METRICS_LOG)
            .where(TCP_METRICS_LOG.MONITOR_ID.eq(monitorId))

    fun fetchLastByMonitorId(monitorId: Long): TcpMetricsLogDto? = dslContext
        .metricsLogDtoSelect(monitorId)
        .orderBy(TCP_METRICS_LOG.CREATED_AT.desc(), TCP_METRICS_LOG.ID.desc())
        .limit(1)
        .fetchOneInto(TcpMetricsLogDto::class.java)
}
