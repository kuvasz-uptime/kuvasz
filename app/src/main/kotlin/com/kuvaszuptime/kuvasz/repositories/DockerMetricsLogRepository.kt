package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMetricsLogDto
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.services.docker.cpuUsagePercentDecimal
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.time.Duration

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
    }

    /**
     * [stats] is null whenever the container was not sampled: the monitor has no metrics history, the container was
     * not running, or the daemon could not answer the sampling call. The row is still written for the latency, which
     * every check produces.
     */
    fun insertLog(monitorId: Long, latencyMs: Int, stats: DockerContainerStats?) {
        dslContext.insertInto(DOCKER_METRICS_LOG)
            .set(
                DockerMetricsLogRecord()
                    .setMonitorId(monitorId)
                    .setLatencyMs(latencyMs)
                    .setCpuUsagePercent(stats?.cpuUsagePercentDecimal)
                    .setMemoryUsageBytes(stats?.memoryUsageBytes)
                    .setMemoryLimitBytes(stats?.memoryLimitBytes)
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

    override fun DSLContext.logDtoSelect(monitorId: Long) =
        select(
            DOCKER_METRICS_LOG.ID.`as`(DockerMetricsLogDto::id.name),
            DOCKER_METRICS_LOG.CPU_USAGE_PERCENT.`as`(DockerMetricsLogDto::cpuUsagePercent.name),
            DOCKER_METRICS_LOG.MEMORY_USAGE_BYTES.`as`(DockerMetricsLogDto::memoryUsageBytes.name),
            DOCKER_METRICS_LOG.MEMORY_LIMIT_BYTES.`as`(DockerMetricsLogDto::memoryLimitBytes.name),
            DOCKER_METRICS_LOG.CREATED_AT.`as`(DockerMetricsLogDto::createdAt.name),
        )
            .from(DOCKER_METRICS_LOG)
            .where(DOCKER_METRICS_LOG.MONITOR_ID.eq(monitorId))
}
