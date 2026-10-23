package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerRestartPointDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerSeriesDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMetricsLogDto
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.services.docker.cpuUsagePercentDecimal
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.DSL.lag
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class DockerMetricsLogRepository(dslContext: DSLContext) :
    MonitorMetricsLogRepository<DockerMetricsLogRecord, DockerMetricsLogDto>(
        dslContext,
        MetricsLogTable(
            table = DOCKER_METRICS_LOG,
            id = DOCKER_METRICS_LOG.ID,
            monitorId = DOCKER_METRICS_LOG.MONITOR_ID,
            createdAt = DOCKER_METRICS_LOG.CREATED_AT,
            latency = DOCKER_METRICS_LOG.LATENCY_MS,
        ),
        DockerMetricsLogDto::class.java,
    ) {

    companion object {
        // Matches the NUMERIC(7, 2) the cpu_usage_percent column is declared with
        const val CPU_USAGE_PERCENT_SCALE = 2

        private const val PREVIOUS_RESTART_COUNT = "previous_restart_count"
        private const val PREVIOUS_CREATED_AT = "previous_created_at"
        private const val PREVIOUS_ID = "previous_id"
    }

    /**
     * [stats] is null whenever the container was not sampled: the monitor has no metrics history, the container was
     * not running, or the daemon could not answer the sampling call. The row is still written for the latency, which
     * every check produces.
     */
    fun insertLog(
        monitorId: Long,
        latencyMs: Int,
        stats: DockerContainerStats?,
        restartCount: Int? = null,
        containerCreatedAt: OffsetDateTime? = null,
    ) {
        dslContext.insertInto(DOCKER_METRICS_LOG)
            .set(
                DockerMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setCpuUsagePercent(stats?.cpuUsagePercentDecimal)
                    .setMemoryUsageBytes(stats?.memoryUsageBytes)
                    .setMemoryLimitBytes(stats?.memoryLimitBytes)
                    .setRestartCount(restartCount)
                    .setContainerCreatedAt(containerCreatedAt)
                    .setCreatedAt(getCurrentTimestamp())
            )
            .execute()
    }

    /**
     * The API latency is kept out of [DockerMetricsLogDto], because it measures the daemon and not the container,
     * so the metrics exporter reads the column straight instead of going through the DTO.
     */
    fun fetchLastLatencyByMonitorId(monitorId: Long): Int? = dslContext
        .select(DOCKER_METRICS_LOG.LATENCY_MS)
        .from(DOCKER_METRICS_LOG)
        .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
        .orderBy(DOCKER_METRICS_LOG.CREATED_AT.desc(), DOCKER_METRICS_LOG.ID.desc())
        .limit(1)
        .fetchOne(DOCKER_METRICS_LOG.LATENCY_MS)

    fun getCpuUsageMetrics(monitorId: Long, period: Duration): CpuUsageMetricResult? =
        getCpuUsageMetricsBetween(monitorId, from = periodStart(period), to = null)

    fun getCpuUsageMetricsBetween(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?) =
        aggregate(DOCKER_METRICS_LOG.CPU_USAGE_PERCENT, monitorId, from, to, CpuUsageMetricResult::class.java)

    fun getMemoryUsageMetrics(monitorId: Long, period: Duration): MemoryUsageMetricResult? =
        getMemoryUsageMetricsBetween(monitorId, from = periodStart(period), to = null)

    fun getMemoryUsageMetricsBetween(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?) =
        aggregate(DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES, monitorId, from, to, MemoryUsageMetricResult::class.java)

    fun fetchDockerSeries(
        monitorId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime?,
        width: Duration,
    ): DockerSeriesDto {
        val cpuUsage = chartQueries.measurement(DOCKER_METRICS_LOG.CPU_USAGE_PERCENT, "cpu_usage")
        val memoryUsage = chartQueries.measurement(DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES, "memory_usage")
        // A boundary rather than a reading, so it has no average or lowest value of its own
        val memoryLimit = DSL.max(DOCKER_METRICS_LOG.MEMORY_LIMIT_BYTES).`as`("memory_limit")
        val withoutReading = chartQueries.checksWithoutReading(DOCKER_METRICS_LOG.CPU_USAGE_PERCENT)
        val buckets = chartQueries.fetchBuckets(
            monitorId = monitorId,
            from = from,
            to = to,
            width = width,
            selected = listOf(
                withoutReading,
                cpuUsage.average,
                cpuUsage.lowest,
                cpuUsage.highest,
                memoryUsage.average,
                memoryUsage.lowest,
                memoryUsage.highest,
                memoryLimit,
            ),
        )
        return DockerSeriesDto(
            timestamps = buckets.getValues(chartQueries.bucketTimestamp),
            checks = buckets.getValues(chartQueries.checks),
            checksWithoutReading = buckets.getValues(withoutReading),
            cpuUsagePercentages = buckets.getValues(cpuUsage.average),
            lowestCpuUsagePercentages = buckets.getValues(cpuUsage.lowest),
            highestCpuUsagePercentages = buckets.getValues(cpuUsage.highest),
            memoryUsageBytes = buckets.getValues(memoryUsage.average),
            lowestMemoryUsageBytes = buckets.getValues(memoryUsage.lowest),
            highestMemoryUsageBytes = buckets.getValues(memoryUsage.highest),
            memoryLimitBytes = buckets.getValues(memoryLimit),
        )
    }

    /**
     * The checks between [from] and [to] (both inclusive, [to] can be open) whose restart count or
     * container creation time differs from the previous check with a count, the oldest first. The first point is the
     * baseline the next one is compared to: the last check with a count before [from], or without one, the first
     * check of the period. This way a restart is found even if it's the first check of the period. The checks without
     * a count are left out, the same way the restart markers of the chart skip them.
     */
    fun fetchRestarts(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?): List<DockerRestartPointDto> {
        val checks = countedChecksWithBaseline(monitorId, from, to)
        val createdAt = checkNotNull(checks.field(DOCKER_METRICS_LOG.CREATED_AT))
        val id = checkNotNull(checks.field(DOCKER_METRICS_LOG.ID))
        val restartCount = checkNotNull(checks.field(DOCKER_METRICS_LOG.RESTART_COUNT))
        val containerCreatedAt = checkNotNull(checks.field(DOCKER_METRICS_LOG.CONTAINER_CREATED_AT))
        val ordering = listOf(createdAt, id)
        val counted = dslContext
            .select(
                createdAt,
                id,
                restartCount,
                containerCreatedAt,
                lag(restartCount).over().orderBy(ordering).`as`(PREVIOUS_RESTART_COUNT),
                lag(containerCreatedAt).over().orderBy(ordering).`as`(PREVIOUS_CREATED_AT),
                lag(id).over().orderBy(ordering).`as`(PREVIOUS_ID),
            )
            .from(checks)
            .asTable("counted")
        val countedCreatedAt = checkNotNull(counted.field(createdAt))
        val countedRestartCount = checkNotNull(counted.field(restartCount))
        val countedContainerCreatedAt = checkNotNull(counted.field(containerCreatedAt))
        return dslContext
            .select(
                epochMillisOf(countedCreatedAt).`as`(DockerRestartPointDto::timestamp.name),
                countedRestartCount.`as`(DockerRestartPointDto::restartCount.name),
                epochMillisOf(countedContainerCreatedAt).`as`(DockerRestartPointDto::containerCreatedAt.name),
            )
            .from(counted)
            .where(checkNotNull(counted.field(PREVIOUS_ID)).isNull)
            .or(countedRestartCount.isDistinctFrom(counted.field(PREVIOUS_RESTART_COUNT, Int::class.java)))
            .or(
                countedContainerCreatedAt.isDistinctFrom(counted.field(PREVIOUS_CREATED_AT, OffsetDateTime::class.java))
            )
            .orderBy(countedCreatedAt, checkNotNull(counted.field(id)))
            .fetchInto(DockerRestartPointDto::class.java)
    }

    /** The checks with a restart count between [from] and [to], and the last one before [from], if there is one. */
    private fun countedChecksWithBaseline(monitorId: Long, from: OffsetDateTime, to: OffsetDateTime?): Table<Record> {
        val columns = listOf(
            DOCKER_METRICS_LOG.CREATED_AT,
            DOCKER_METRICS_LOG.ID,
            DOCKER_METRICS_LOG.RESTART_COUNT,
            DOCKER_METRICS_LOG.CONTAINER_CREATED_AT,
        )
        val baseline = dslContext
            .select(columns)
            .from(DOCKER_METRICS_LOG)
            .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(DOCKER_METRICS_LOG.CREATED_AT.lessThan(from))
            .and(DOCKER_METRICS_LOG.RESTART_COUNT.isNotNull)
            .orderBy(DOCKER_METRICS_LOG.CREATED_AT.desc(), DOCKER_METRICS_LOG.ID.desc())
            .limit(1)
        val period = dslContext
            .select(columns)
            .from(DOCKER_METRICS_LOG)
            .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(isBetween(DOCKER_METRICS_LOG.CREATED_AT, from, to))
            .and(DOCKER_METRICS_LOG.RESTART_COUNT.isNotNull)
        return baseline.unionAll(period).asTable("checks")
    }

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            DOCKER_METRICS_LOG.ID.`as`(DockerMetricsLogDto::id.name),
            DOCKER_METRICS_LOG.CPU_USAGE_PERCENT.`as`(DockerMetricsLogDto::cpuUsagePercent.name),
            DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES.`as`(DockerMetricsLogDto::memoryUsageBytes.name),
            DOCKER_METRICS_LOG.MEMORY_LIMIT_BYTES.`as`(DockerMetricsLogDto::memoryLimitBytes.name),
            DOCKER_METRICS_LOG.RESTART_COUNT.`as`(DockerMetricsLogDto::restartCount.name),
            DOCKER_METRICS_LOG.CONTAINER_CREATED_AT.`as`(DockerMetricsLogDto::containerCreatedAt.name),
            DOCKER_METRICS_LOG.CREATED_AT.`as`(DockerMetricsLogDto::createdAt.name),
        )
            .from(DOCKER_METRICS_LOG)
            .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
