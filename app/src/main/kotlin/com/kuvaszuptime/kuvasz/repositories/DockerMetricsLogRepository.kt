package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerChartPointDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.chart.DockerRestartPointDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMetricsLogDto
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.services.docker.cpuUsagePercentDecimal
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
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
        aggregate(DOCKER_METRICS_LOG.CPU_USAGE_PERCENT, monitorId, period, CpuUsageMetricResult::class.java)

    fun getMemoryUsageMetrics(monitorId: Long, period: Duration): MemoryUsageMetricResult? =
        aggregate(DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES, monitorId, period, MemoryUsageMetricResult::class.java)

    fun fetchDockerChartPoints(monitorId: Long, period: Duration, resolution: Duration?): List<DockerChartPointDto> =
        chartQueries.fetchPoints(
            monitorId = monitorId,
            period = period,
            resolution = resolution,
            createdAtAlias = DockerChartPointDto::createdAt.name,
            measurements = listOf(
                chartQueries.measurement(
                    measurement = DOCKER_METRICS_LOG.CPU_USAGE_PERCENT,
                    alias = DockerChartPointDto::cpuUsagePercent.name,
                    maxAlias = DockerChartPointDto::maxCpuUsagePercent.name,
                ),
                chartQueries.measurement(
                    measurement = DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES,
                    alias = DockerChartPointDto::memoryUsageBytes.name,
                    maxAlias = DockerChartPointDto::maxMemoryUsageBytes.name,
                ),
                // A boundary rather than a reading, so it has no band of its own
                chartQueries.measurement(
                    measurement = DOCKER_METRICS_LOG.MEMORY_LIMIT_BYTES,
                    alias = DockerChartPointDto::memoryLimitBytes.name,
                ),
            ),
            pointType = DockerChartPointDto::class.java,
        )

    /**
     * The checks of the period whose restart count or container creation time differs from the previous check with a
     * count (the first one of the period included), newest first. The checks without a count are left out, the same
     * way the restart markers of the chart skip them.
     */
    fun fetchRestartPoints(monitorId: Long, period: Duration): List<DockerRestartPointDto> {
        val ordering = listOf(DOCKER_METRICS_LOG.CREATED_AT, DOCKER_METRICS_LOG.ID)
        val counted = dslContext
            .select(
                DOCKER_METRICS_LOG.CREATED_AT,
                DOCKER_METRICS_LOG.ID,
                DOCKER_METRICS_LOG.RESTART_COUNT,
                DOCKER_METRICS_LOG.CONTAINER_CREATED_AT,
                lag(DOCKER_METRICS_LOG.RESTART_COUNT).over().orderBy(ordering).`as`(PREVIOUS_RESTART_COUNT),
                lag(DOCKER_METRICS_LOG.CONTAINER_CREATED_AT).over().orderBy(ordering).`as`(PREVIOUS_CREATED_AT),
                lag(DOCKER_METRICS_LOG.ID).over().orderBy(ordering).`as`(PREVIOUS_ID),
            )
            .from(DOCKER_METRICS_LOG)
            .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
            .and(DOCKER_METRICS_LOG.CREATED_AT.greaterOrEqual(getCurrentTimestamp().minusSeconds(period.toSeconds())))
            .and(DOCKER_METRICS_LOG.RESTART_COUNT.isNotNull)
            .asTable("counted")
        val createdAt = checkNotNull(counted.field(DOCKER_METRICS_LOG.CREATED_AT))
        val restartCount = checkNotNull(counted.field(DOCKER_METRICS_LOG.RESTART_COUNT))
        val containerCreatedAt = checkNotNull(counted.field(DOCKER_METRICS_LOG.CONTAINER_CREATED_AT))
        return dslContext
            .select(
                createdAt.`as`(DockerRestartPointDto::createdAt.name),
                restartCount.`as`(DockerRestartPointDto::restartCount.name),
                containerCreatedAt.`as`(DockerRestartPointDto::containerCreatedAt.name),
            )
            .from(counted)
            .where(checkNotNull(counted.field(PREVIOUS_ID)).isNull)
            .or(restartCount.isDistinctFrom(counted.field(PREVIOUS_RESTART_COUNT, Int::class.java)))
            .or(containerCreatedAt.isDistinctFrom(counted.field(PREVIOUS_CREATED_AT, OffsetDateTime::class.java)))
            .orderBy(createdAt.desc(), checkNotNull(counted.field(DOCKER_METRICS_LOG.ID)).desc())
            .fetchInto(DockerRestartPointDto::class.java)
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
