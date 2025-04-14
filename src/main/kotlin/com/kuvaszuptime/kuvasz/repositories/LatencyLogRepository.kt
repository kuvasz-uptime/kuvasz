package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.daos.LatencyLogDao
import com.kuvaszuptime.kuvasz.tables.pojos.LatencyLogPojo
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import org.jooq.Configuration
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.min
import java.time.OffsetDateTime

@Singleton
class LatencyLogRepository(jooqConfig: Configuration) : LatencyLogDao(jooqConfig) {

    companion object {
        private const val P95 = .95
        private const val P99 = .99
    }

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

    fun deleteAllByMonitorId(monitorId: Int) =
        dsl.delete(LATENCY_LOG)
            .where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
            .execute()

    // Well well, that's not so performant in case of a really huge dataset. Definitely something that should be
    // improved in the future.
    fun getLatencyPercentiles(monitorId: Int? = null): List<PercentileResult> =
        dsl
            .with("percentiles").`as`(
                DSL.selectDistinct(
                    LATENCY_LOG.MONITOR_ID.`as`("monitor_id"),
                    DSL.round(LATENCY_LOG.LATENCY, -1).`as`("latency"),
                    DSL.percentRank().over()
                        .partitionBy(LATENCY_LOG.MONITOR_ID).orderBy(DSL.round(LATENCY_LOG.LATENCY, -1))
                        .`as`("percentile")
                ).from(LATENCY_LOG)
                    .apply {
                        if (monitorId != null) {
                            where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
                        }
                    }
            )
            .select(
                field("p1.monitor_id").`as`("monitorId"),
                min(field("p1.latency", Int::class.java)).`as`("p95"),
                min(field("p2.latency", Int::class.java)).`as`("p99")
            )
            .from(DSL.table("percentiles").`as`("p1"))
            .join(DSL.table("percentiles").`as`("p2"))
            .on(field("p1.monitor_id", Int::class.java).eq(field("p2.monitor_id", Int::class.java)))
            .where(field("p1.percentile", Double::class.java).greaterOrEqual(P95))
            .and(field("p2.percentile", Double::class.java).greaterOrEqual(P99))
            .groupBy(field("p1.monitor_id"))
            .fetchInto(PercentileResult::class.java)
}

@Introspected
data class PercentileResult(
    val monitorId: Int,
    val p95: Int?,
    val p99: Int?
)
