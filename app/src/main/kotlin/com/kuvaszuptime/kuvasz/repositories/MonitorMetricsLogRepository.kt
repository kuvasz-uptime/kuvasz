package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.LatencySeriesDto
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Result
import org.jooq.SelectConditionStep
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.DSL.avg
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.min
import org.jooq.impl.DSL.round
import org.jooq.impl.SQLDataType
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

    protected val chartQueries = MetricsLogChartQueries(dslContext, logTable)

    /**
     * The type specific projection of a monitor's log rows onto its own DTO.
     */
    protected abstract fun DSLContext.logDtoSelect(monitorId: Long): SelectConditionStep<out Record>

    @Suppress("IgnoredReturnValue")
    fun fetchLatestByMonitorId(monitorId: Long, period: Duration? = null): List<D> = dslContext
        .logDtoSelect(monitorId)
        .apply {
            period?.let { and(logTable.createdAt.greaterOrEqual(periodStart(it))) }
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
        getLatencyMetricsBetween(monitorId, from = periodStart(period), to = null)

    fun getLatencyMetricsBetween(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?): LatencyMetricResult? =
        aggregate(logTable.latency, monitorId, from, to, LatencyMetricResult::class.java, withPercentiles = true)

    /**
     * The average latency of each of the given monitors over the period, in a single query, leaving out the ones
     * without any latency in it. Unlike [getLatencyMetrics], it doesn't sort the logs for the percentiles, so it's
     * cheap enough for a whole status page.
     */
    open fun getAverageLatencies(monitorIds: Collection<Long>, period: Duration): Map<Long, Int> =
        averagesByMonitor(logTable.latency, monitorIds, period)

    /** The average of a measurement of each of the given monitors over the period, see [getAverageLatencies]. */
    protected fun <N : Number> averagesByMonitor(
        measurement: TableField<R, N>,
        monitorIds: Collection<Long>,
        period: Duration,
    ): Map<Long, N> {
        if (monitorIds.isEmpty()) return emptyMap()
        val average = avg(measurement).asMeasurementOf(measurement).`as`(MetricResult<*>::avg.name)
        return dslContext
            .select(logTable.monitorId, average)
            .from(logTable.table)
            .where(logTable.monitorId.`in`(monitorIds))
            .and(logTable.createdAt.greaterOrEqual(periodStart(period)))
            .and(measurement.isNotNull)
            .groupBy(logTable.monitorId)
            .fetchMap(logTable.monitorId, average)
    }

    fun fetchLatencySeries(
        monitorId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        width: Duration,
    ): LatencySeriesDto {
        val latency = chartQueries.measurement(logTable.latency, "latency")
        val buckets = chartQueries.fetchBuckets(
            monitorId, from, to, width, listOf(latency.average, latency.lowest, latency.highest),
        )
        return LatencySeriesDto(
            timestamps = buckets.getValues(chartQueries.bucketTimestamp),
            latencies = buckets.getValues(latency.average),
            lowestLatencies = buckets.getValues(latency.lowest),
            highestLatencies = buckets.getValues(latency.highest),
        )
    }

    /**
     * Aggregates a measurement of a monitor's logs between [from] and [to] (both inclusive, [to] can be open). Rows
     * where the measurement is null are left out, they carry no reading at all - a container that was not sampled has
     * no CPU reading, for example. [withPercentiles] adds the percentiles, which only the timings and the loss ratios
     * are read by.
     *
     * The result is reported in the measurement's own type, which [asMeasurementOf] brings the average back to,
     * since the database answers it in its own decimal type whatever the column is.
     */
    protected fun <N : Number, T : Any> aggregate(
        measurement: TableField<R, N>,
        monitorId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        resultType: Class<T>,
        withPercentiles: Boolean = false,
    ): T? {
        val percentiles = if (withPercentiles) {
            listOf(
                PercentileMetricResult<*>::p90.name,
                PercentileMetricResult<*>::p95.name,
                PercentileMetricResult<*>::p99.name,
            )
        } else {
            emptyList()
        }
        return dslContext
            .select(
                listOf(
                    logTable.monitorId.`as`(MetricResult<*>::monitorId.name),
                    avg(measurement).asMeasurementOf(measurement).`as`(MetricResult<*>::avg.name),
                    min(measurement).`as`(MetricResult<*>::min.name),
                    max(measurement).`as`(MetricResult<*>::max.name),
                ) + percentiles.mapIndexed { index, alias ->
                    percentileOf(measurement, index).asMeasurementOf(measurement).`as`(alias)
                }
            )
            .from(logTable.table)
            .where(logTable.monitorId.eq(monitorId))
            .and(isBetween(logTable.createdAt, from, to))
            .and(measurement.isNotNull)
            .groupBy(logTable.monitorId)
            .fetchOneInto(resultType)
    }
}

/**
 * The queries of the charts, which group the logs of a monitor in a period into buckets of equal length in the
 * database, so a chart gets a bounded number of points however many checks the period has.
 */
class MetricsLogChartQueries<R : Record>(
    private val dslContext: DSLContext,
    private val logTable: MetricsLogTable<R>,
) {

    /**
     * The average, the lowest and the highest reading of a measurement in each bucket, the null readings (e.g. of a
     * failed check) left out. They are all null for a bucket that has no reading at all.
     */
    class BucketedMeasurement<N : Number>(val average: Field<N>, val lowest: Field<N>, val highest: Field<N>)

    // The timestamp of a bucket is the average timestamp of its checks, not its start, so it's where its checks really
    // are, and the bucket of a single check is that check exactly
    val bucketTimestamp: Field<Long> = DSL
        .field("floor(avg(extract(epoch from {0})) * 1000)::bigint", SQLDataType.BIGINT, logTable.createdAt)
        .`as`("bucket_timestamp")

    fun <N : Number> measurement(field: TableField<R, N>, alias: String) = BucketedMeasurement(
        average = avg(field).asMeasurementOf(field).`as`("${alias}_average"),
        lowest = min(field).`as`("${alias}_lowest"),
        highest = max(field).`as`("${alias}_highest"),
    )

    /**
     * Fetches the buckets of the given [width] of a monitor's logs between [from] and [to], both inclusive, [to] can
     * be open, the oldest first. The buckets are aligned to the epoch, so to whole hours, quarters, etc.
     */
    fun fetchBuckets(
        monitorId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        width: Duration,
        selected: List<Field<*>>,
    ): Result<Record> {
        val bucket = DSL.field(
            "floor(extract(epoch from {0}) / {1})",
            SQLDataType.NUMERIC,
            logTable.createdAt,
            inline(width.toSeconds()),
        )
        // Ordered outside of the grouping, so the database can hash the logs into buckets instead of sorting all of
        // them first, which spills to disk for a long period. A bucket's timestamp lies within the bucket.
        val buckets = dslContext
            .select(listOf(bucketTimestamp) + selected)
            .from(logTable.table)
            .where(logTable.monitorId.eq(monitorId))
            .and(isBetween(logTable.createdAt, from, to))
            .groupBy(bucket)
            .asTable("buckets")
        return dslContext
            .selectFrom(buckets)
            .orderBy(checkNotNull(buckets.field(bucketTimestamp)))
            .fetch()
    }
}

private val PERCENTILE_FRACTIONS = listOf(P90, P95, P99)

/**
 * One of the [PERCENTILE_FRACTIONS] of a measurement. Asked for as the elements of a single array-valued aggregate,
 * the database sorts the readings once for all of them, which it would do for each of them as separate aggregates.
 * The database answers them as double precision numbers, the same as for a percentile of its own.
 */
private fun percentileOf(measurement: TableField<*, *>, index: Int): Field<BigDecimal> {
    val fractions = PERCENTILE_FRACTIONS.joinToString()
    return DSL.field(
        "(percentile_cont(array[$fractions]) within group (order by {0}))[${index + 1}]",
        SQLDataType.NUMERIC,
        measurement,
    )
}

fun isBetween(timestamp: Field<OffsetDateTime>, from: OffsetDateTime, to: OffsetDateTime?): Condition =
    timestamp.greaterOrEqual(from).and(to?.let { timestamp.lessOrEqual(it) } ?: DSL.noCondition())

fun periodStart(period: Duration): OffsetDateTime = getCurrentTimestamp().minusSeconds(period.toSeconds())

fun epochMillisOf(timestamp: Field<OffsetDateTime>): Field<Long> =
    DSL.field("floor(extract(epoch from {0}) * 1000)::bigint", SQLDataType.BIGINT, timestamp)

/**
 * Brings a computed decimal back to the type of the column it was computed from: an integral measurement is
 * rounded to a whole unit, a fractional one only has its scale trimmed, because rounding it to a whole unit
 * would throw away everything the column keeps decimals for.
 */
private fun <R : Record, N : Number> Field<BigDecimal>.asMeasurementOf(measurement: TableField<R, N>): Field<N> =
    if (measurement.type == BigDecimal::class.java) {
        round(this, measurement.dataType.scale()).coerce(measurement.dataType)
    } else {
        round(this).cast(measurement.dataType)
    }
