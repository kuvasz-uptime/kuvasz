package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.DecimalGaugeExporter
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
import java.math.BigDecimal

/**
 * The container's CPU usage, which only a monitor with a metrics history produces: the sampling costs an extra
 * Docker API call, so a monitor without one reports nothing here and gets no meter at all.
 */
@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-latest-cpu-usage", value = StringUtils.TRUE),
)
class DockerCpuUsageExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: DockerMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : DecimalGaugeExporter<BigDecimal, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_CPU_USAGE = "docker.cpu.usage.latest.percent"
    }

    override val meterName = MONITOR_CPU_USAGE

    override fun subscribeToEvents() {
        // Only the up events carry a sample: a container that is not running has no live cgroup to read
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            val cpuUsage = event.cpuUsagePercent ?: return@subscribeToDockerMonitorUpEvents
            logger.debug("Updating CPU usage for monitor with ID: ${event.monitor.id} to $cpuUsage")
            upsertMeter(event.monitor.numericMonitorId(), cpuUsage)
        }
    }

    override fun transform(valueSource: BigDecimal): Double = valueSource.toDouble()

    override fun computeInitialValue(monitor: DockerMonitorRecord): BigDecimal? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.cpuUsagePercent

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean =
        monitor.enabled && monitor.metricsHistoryEnabled
}
