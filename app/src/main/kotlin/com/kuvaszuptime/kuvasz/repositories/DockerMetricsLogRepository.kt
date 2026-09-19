package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.Tables.DOCKER_METRICS_LOG
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMetricsLogRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMetricsLogDto
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import org.jooq.DSLContext
import java.math.RoundingMode

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

    private companion object {
        const val CPU_PERCENT_SCALE = 2
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
                    .setCpuUsagePercent(
                        stats?.cpuUsagePercent?.toBigDecimal()?.setScale(CPU_PERCENT_SCALE, RoundingMode.HALF_UP)
                    )
                    .setMemoryUsageBytes(stats?.memoryUsageBytes)
                    .setMemoryLimitBytes(stats?.memoryLimitBytes)
                    .setCreatedAt(getCurrentTimestamp())
            )
            .execute()
    }

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
