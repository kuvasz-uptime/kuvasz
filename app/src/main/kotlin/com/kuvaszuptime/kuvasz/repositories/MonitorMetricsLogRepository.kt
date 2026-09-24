package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SelectConditionStep
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.percentileCont
import org.jooq.impl.DSL.round
import java.math.BigDecimal
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

/**
 * The queries that are the same for every monitor type's metrics log, parameterized with the table they run against.
 *
 * [getLatencyMetrics] is explicitly `open` because the tests mock it on the inheriting repositories. Unlike them,
 * this base is not a `@Singleton`, so the `allOpen` plugin does not reach it, and a final method inherited from here
 * would silently fall through to the real query instead of the stub.
 */
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

    open fun getLatencyMetrics(monitorId: Long, period: Duration): LatencyMetricResult? =
        aggregateWithPercentiles(logTable.latency, monitorId, period, LatencyMetricResult::class.java)

    /**
     * Aggregates a measurement of a monitor's logs over the given period. Rows where the measurement is null are left
     * out, they carry no reading at all - a container that was not sampled has no CPU reading, for example.
     *
     * The result is reported in the measurement's own type, which [asMeasurementOf] brings the average back to,
     * since the database answers it in its own decimal type whatever the column is.
     */
    protected fun <N : Number, T : Any> aggregate(
        measurement: TableField<R, N>,
        monitorId: Long,
        period: Duration,
        resultType: Class<T>,
    ): T? = aggregateSelect(measurement, emptyList(), monitorId, period, resultType)

    /**
     * The same aggregation, extended with the percentiles that only the timings and the loss ratios are read by.
     */
    protected fun <N : Number, T : Any> aggregateWithPercentiles(
        measurement: TableField<R, N>,
        monitorId: Long,
        period: Duration,
        resultType: Class<T>,
    ): T? = aggregateSelect(
        measurement = measurement,
        percentiles = listOf(
            P90 to PercentileMetricResult<*>::p90.name,
            P95 to PercentileMetricResult<*>::p95.name,
            P99 to PercentileMetricResult<*>::p99.name,
        ),
        monitorId = monitorId,
        period = period,
        resultType = resultType,
    )

    private fun <N : Number, T : Any> aggregateSelect(
        measurement: TableField<R, N>,
        percentiles: List<Pair<Double, String>>,
        monitorId: Long,
        period: Duration,
        resultType: Class<T>,
    ): T? = dslContext
        .select(
            listOf(
                logTable.monitorId.`as`(MetricResult<*>::monitorId.name),
                avg(measurement).asMeasurementOf(measurement).`as`(MetricResult<*>::avg.name),
                min(measurement).`as`(MetricResult<*>::min.name),
                max(measurement).`as`(MetricResult<*>::max.name),
            ) + percentiles.map { (fraction, alias) ->
                percentileCont(fraction).withinGroupOrderBy(measurement).asMeasurementOf(measurement).`as`(alias)
            }
        )
        .from(logTable.table)
        .where(logTable.monitorId.eq(monitorId))
        .and(logTable.createdAt.greaterOrEqual(getCurrentTimestamp().minusSeconds(period.toSeconds())))
        .and(measurement.isNotNull)
        .groupBy(logTable.monitorId)
        .fetchOneInto(resultType)

    /**
     * Brings a computed decimal back to the type of the column it was computed from: an integral measurement is
     * rounded to a whole unit, a fractional one only has its scale trimmed, because rounding it to a whole unit
     * would throw away everything the column keeps decimals for.
     */
    private fun <N : Number> Field<BigDecimal>.asMeasurementOf(measurement: TableField<R, N>): Field<N> =
        if (measurement.type == BigDecimal::class.java) {
            round(this, DECIMAL_SCALE).coerce(measurement.dataType)
        } else {
            round(this).cast(measurement.dataType)
        }

    private companion object {
        /** Matches the scale the fractional metrics log columns are declared with. */
        const val DECIMAL_SCALE = 2
    }
}
