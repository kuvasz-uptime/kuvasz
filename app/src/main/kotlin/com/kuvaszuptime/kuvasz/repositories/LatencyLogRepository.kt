package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.LatencyLogRecord
import com.kuvaszuptime.kuvasz.models.dto.LatencyLogDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.core.annotation.Introspected
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
class LatencyLogRepository(private val dslContext: DSLContext) {

    companion object {
        private const val P90 = .90
        private const val P95 = .95
        private const val P99 = .99
    }

    fun insertLatencyForMonitor(monitorId: Long, latency: Int, createdAt: OffsetDateTime = getCurrentTimestamp()) {
        dslContext.insertInto(LATENCY_LOG)
            .set(
                LatencyLogRecord()
                    .setMonitorId(monitorId)
                    .setLatency(latency)
                    .setCreatedAt(createdAt)
            )
            .execute()
    }

    private fun DSLContext.latencyLogDtoSelect(monitorId: Long) =
        select(
            LATENCY_LOG.ID.`as`(LatencyLogDto::id.name),
            LATENCY_LOG.LATENCY.`as`(LatencyLogDto::latencyInMs.name),
            LATENCY_LOG.CREATED_AT.`as`(LatencyLogDto::createdAt.name)
        )
            .from(LATENCY_LOG)
            .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(
        monitorId: Long,
        period: Duration? = null,
    ): List<LatencyLogDto> = dslContext
        .latencyLogDtoSelect(monitorId)
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(LATENCY_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(LATENCY_LOG.CREATED_AT.desc(), LATENCY_LOG.ID.desc())
        .fetchInto(LatencyLogDto::class.java)

    fun fetchLastByMonitorId(monitorId: Long): LatencyLogDto? = dslContext
        .latencyLogDtoSelect(monitorId)
        .orderBy(LATENCY_LOG.CREATED_AT.desc(), LATENCY_LOG.ID.desc())
        .limit(1)
        .fetchOneInto(LatencyLogDto::class.java)

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.CREATED_AT.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    fun getLatencyMetrics(monitorId: Long, period: Duration): LatencyMetricResult? {
        val thresholdSeconds = period.toSeconds()
        return dslContext
            .select(
                LATENCY_LOG.MONITOR_ID.`as`(LatencyMetricResult::monitorId.name),
                round(avg(LATENCY_LOG.LATENCY)).cast(Int::class.java).`as`(LatencyMetricResult::avg.name),
                min(LATENCY_LOG.LATENCY).`as`(LatencyMetricResult::min.name),
                max(LATENCY_LOG.LATENCY).`as`(LatencyMetricResult::max.name),
                round(percentileCont(P90).withinGroupOrderBy(LATENCY_LOG.LATENCY)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p90.name),
                round(percentileCont(P95).withinGroupOrderBy(LATENCY_LOG.LATENCY)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p95.name),
                round(percentileCont(P99).withinGroupOrderBy(LATENCY_LOG.LATENCY)).cast(Int::class.java)
                    .`as`(LatencyMetricResult::p99.name)
            )
            .from(LATENCY_LOG)
            .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
            .and(LATENCY_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            .groupBy(LATENCY_LOG.MONITOR_ID)
            .fetchOneInto(LatencyMetricResult::class.java)
    }
}

@Introspected
data class LatencyMetricResult(
    val monitorId: Long,
    val avg: Int?,
    val min: Int?,
    val max: Int?,
    val p90: Int?,
    val p95: Int?,
    val p99: Int?,
)
