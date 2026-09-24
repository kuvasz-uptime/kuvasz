package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.docker.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

/**
 * The container's memory usage in bytes, reported the way `docker stats` computes it: the raw usage less the page
 * cache. Like the CPU gauge it exists only for monitors that keep a metrics history.
 */
@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-latest-memory-usage", value = StringUtils.TRUE),
)
class DockerMemoryUsageExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: DockerMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Long, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_MEMORY_USAGE = "docker.memory.usage.latest.bytes"
    }

    override val meterName = MONITOR_MEMORY_USAGE

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            val memoryUsage = event.memoryUsageBytes ?: return@subscribeToDockerMonitorUpEvents
            logger.debug("Updating memory usage for monitor with ID: ${event.monitor.id} to $memoryUsage")
            upsertMeter(event.monitor.numericMonitorId(), memoryUsage)
        }
    }

    override fun transform(valueSource: Long): Long = valueSource

    override fun computeInitialValue(monitor: DockerMonitorRecord): Long? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.memoryUsageBytes

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean =
        monitor.enabled && monitor.metricsHistoryEnabled
}
