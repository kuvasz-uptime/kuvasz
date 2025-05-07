package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.LatencyLogDto
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.records.LatencyLogRecord
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.percentileCont
import org.jooq.impl.DSL.round
import java.time.OffsetDateTime

@Singleton
class LatencyLogRepository(private val dslContext: DSLContext) {

    companion object {
        private const val P95 = .95
        private const val P99 = .99
    }

    fun insertLatencyForMonitor(monitorId: Long, latency: Int) {
        dslContext.insertInto(LATENCY_LOG)
            .set(
                LatencyLogRecord()
                    .setMonitorId(monitorId)
                    .setLatency(latency)
            )
            .execute()
    }

    fun fetchLatestByMonitorId(
        monitorId: Long,
        limit: Int? = null,
    ): List<LatencyLogDto> = dslContext
        .select(
            LATENCY_LOG.ID.`as`(LatencyLogDto::id.name),
            LATENCY_LOG.LATENCY.`as`(LatencyLogDto::latencyInMs.name),
            LATENCY_LOG.CREATED_AT.`as`(LatencyLogDto::createdAt.name)
        )
        .from(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .apply {
            if (limit != null) {
                @Suppress("IgnoredReturnValue")
                limit(limit)
            }
        }
        .orderBy(LATENCY_LOG.CREATED_AT.desc(), LATENCY_LOG.ID.desc())
        .fetchInto(LatencyLogDto::class.java)

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.CREATED_AT.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    fun getLatencyMetrics(monitorId: Long): LatencyMetricResult? = dslContext
        .select(
            LATENCY_LOG.MONITOR_ID.`as`(LatencyMetricResult::monitorId.name),
            round(avg(LATENCY_LOG.LATENCY)).cast(Int::class.java).`as`(LatencyMetricResult::avg.name),
            round(percentileCont(P95).withinGroupOrderBy(LATENCY_LOG.LATENCY)).cast(Int::class.java)
                .`as`(LatencyMetricResult::p95.name),
            round(percentileCont(P99).withinGroupOrderBy(LATENCY_LOG.LATENCY)).cast(Int::class.java)
                .`as`(LatencyMetricResult::p99.name)
        )
        .from(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .groupBy(LATENCY_LOG.MONITOR_ID)
        .fetchOneInto(LatencyMetricResult::class.java)
}

@Introspected
data class LatencyMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val p95: Int?,
    val p99: Int?
)
