package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.tables.LatencyLog.LATENCY_LOG
import com.kuvaszuptime.kuvasz.tables.daos.LatencyLogDao
import com.kuvaszuptime.kuvasz.tables.pojos.LatencyLogPojo
import io.micronaut.core.annotation.Introspected
import org.jooq.Configuration
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.orderBy
import org.jooq.impl.DSL.percentRank
import org.jooq.impl.DSL.select
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LatencyLogRepository @Inject constructor(jooqConfig: Configuration) : LatencyLogDao(jooqConfig) {

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

    fun getLatencyPercentilesForMonitor(monitorId: Int): PercentileResult? {
        val percentileAlias = "percentile"
        val latencyAlias = "latency"
        val rankedAlias = "ranked"
        val percentileField1 = field("rank1.$percentileAlias", Double::class.java)
        val percentileField2 = field("rank2.$percentileAlias", Double::class.java)

        return dsl.with(rankedAlias).`as`(
            select(
                LATENCY_LOG.LATENCY.`as`(latencyAlias),
                percentRank().over(orderBy(LATENCY_LOG.LATENCY)).`as`(percentileAlias)
            ).from(LATENCY_LOG).where(LATENCY_LOG.MONITOR_ID.eq(monitorId))
        )
            .select(
                min(field("rank1.$latencyAlias", Int::class.java)).`as`("p95"),
                min(field("rank2.$latencyAlias", Int::class.java)).`as`("p99")
            )
            .from("$rankedAlias as rank1, $rankedAlias as rank2")
            .where(percentileField1.greaterOrEqual(P95))
            .and(percentileField2.greaterOrEqual(P99))
            .fetchOneInto(PercentileResult::class.java)
    }
}

@Introspected
data class PercentileResult(
    val p95: Int?,
    val p99: Int?
)
