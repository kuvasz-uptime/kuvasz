package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.LatencyLogDto
import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.records.LatencyLogRecord
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.percentRank
import org.jooq.impl.DSL.round
import org.jooq.impl.DSL.selectDistinct
import org.jooq.impl.DSL.table
import java.math.BigDecimal
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

    fun getAverageByMonitorId(monitorId: Long): BigDecimal? = dslContext
        .select(avg(LATENCY_LOG.LATENCY))
        .from(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .fetchOne()
        ?.value1()

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.CREATED_AT.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long) = dslContext
        .delete(LATENCY_LOG)
        .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        .execute()

    // Well well, that's not so performant in case of a really huge dataset. Definitely something that should be
    // improved in the future.
    fun getLatencyPercentiles(monitorId: Long? = null): List<PercentileResult> = dslContext
        .with("percentiles").`as`(
            selectDistinct(
                LATENCY_LOG.MONITOR_ID.`as`("monitor_id"),
                round(LATENCY_LOG.LATENCY, -1).`as`("latency"),
                percentRank().over()
                    .partitionBy(LATENCY_LOG.MONITOR_ID).orderBy(round(LATENCY_LOG.LATENCY, -1))
                    .`as`("percentile")
            ).from(LATENCY_LOG)
                .apply {
                    if (monitorId != null) {
                        @Suppress("IgnoredReturnValue")
                        where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
                    }
                }
        )
        .select(
            field("p1.monitor_id").`as`("monitorId"),
            min(field("p1.latency", Int::class.java)).`as`("p95"),
            min(field("p2.latency", Int::class.java)).`as`("p99")
        )
        .from(table("percentiles").`as`("p1"))
        .join(table("percentiles").`as`("p2"))
        .on(field("p1.monitor_id", Long::class.java).eq(field("p2.monitor_id", Long::class.java)))
        .where(field("p1.percentile", Double::class.java).greaterOrEqual(P95))
        .and(field("p2.percentile", Double::class.java).greaterOrEqual(P99))
        .groupBy(field("p1.monitor_id"))
        .fetchInto(PercentileResult::class.java)
}

@Introspected
data class PercentileResult(
    val monitorId: Long,
    val p95: Int?,
    val p99: Int?
)
