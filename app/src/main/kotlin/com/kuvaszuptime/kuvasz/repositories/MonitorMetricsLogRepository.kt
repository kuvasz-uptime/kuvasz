package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.percentileCont
import org.jooq.impl.DSL.round
import java.time.Duration
import java.time.OffsetDateTime

/**
 * The columns every metrics log table has, regardless of the monitor type it belongs to.
 */
data class MetricsLogTable<R : Record>(
    val table: Table<R>,
    val id: TableField<R, Long>,
    val monitorId: TableField<R, Long>,
    val createdAt: TableField<R, OffsetDateTime>,
    val latency: TableField<R, Int>,
)

abstract class MonitorMetricsLogRepository<R : Record, D : Any>(
    protected val dslContext: DSLContext,
    private val logTable: MetricsLogTable<R>,
    private val dtoType: Class<D>,
) {

    /**
     * The type specific projection of a monitor's log rows onto its own DTO.
     */
    protected abstract fun DSLContext.logDtoSelect(monitorId: Long): SelectConditionStep<out Record>

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<D> = dslContext
        .logDtoSelect(monitorId)
        .apply {
            period?.toSeconds()?.let { thresholdSeconds ->
                and(logTable.createdAt.greaterOrEqual(getCurrentTimestamp().minusSeconds(thresholdSeconds)))
            }
        }
        .orderBy(logTable.createdAt.desc(), logTable.id.desc())
        .fetchInto(dtoType)

    fun fetchLastByMonitorId(monitorId: Long): D? = dslContext
        .logDtoSelect(monitorId)
        .orderBy(logTable.createdAt.desc(), logTable.id.desc())
        .limit(1)
        .fetchOneInto(dtoType)

    fun deleteLogsBeforeDate(limit: OffsetDateTime) = dslContext
        .delete(logTable.table)
        .where(logTable.createdAt.lessThan(limit))
        .execute()

    fun deleteAllByMonitorId(monitorId: Long, txCtx: DSLContext? = null) = (txCtx ?: dslContext)
        .delete(logTable.table)
        .where(logTable.monitorId.eq(monitorId))
        .execute()

    fun getLatencyMetrics(monitorId: Long, period: Duration): LatencyMetricResult? =
        aggregate(logTable.latency, monitorId, period, LatencyMetricResult::class.java)

    /**
     * Aggregates a measurement of a monitor's logs over the given period. Rows where the measurement is null are left
     * out, they carry no reading at all - a down monitor has no latency, for example.
     */
    protected fun <T : Any> aggregate(
        measurement: TableField<R, Int>,
        monitorId: Long,
        period: Duration,
        resultType: Class<T>,
    ): T? = dslContext
        .select(
            logTable.monitorId.`as`(MetricResult::monitorId.name),
            round(avg(measurement)).cast(Int::class.java).`as`(MetricResult::avg.name),
            min(measurement).`as`(MetricResult::min.name),
            max(measurement).`as`(MetricResult::max.name),
            round(percentileCont(P90).withinGroupOrderBy(measurement)).cast(Int::class.java)
                .`as`(MetricResult::p90.name),
            round(percentileCont(P95).withinGroupOrderBy(measurement)).cast(Int::class.java)
                .`as`(MetricResult::p95.name),
            round(percentileCont(P99).withinGroupOrderBy(measurement)).cast(Int::class.java)
                .`as`(MetricResult::p99.name),
        )
        .from(logTable.table)
        .where(logTable.monitorId.eq(monitorId))
        .and(logTable.createdAt.greaterOrEqual(getCurrentTimestamp().minusSeconds(period.toSeconds())))
        .and(measurement.isNotNull)
        .groupBy(logTable.monitorId)
        .fetchOneInto(resultType)
}
